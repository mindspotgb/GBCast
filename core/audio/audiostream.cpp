#include "audiostream.h"
#include "cfg/option.h"
#include "emulator.h"
#include <algorithm>
#include <chrono>

static void registerForEvents();

struct SoundFrame { s16 l; s16 r; };

static SoundFrame Buffer[SAMPLE_COUNT];
static u32 writePtr;  // next sample index

static AudioBackend *currentBackend;
std::vector<AudioBackend *> *AudioBackend::backends;

static bool audio_recording_started;
static bool eight_khz;

AudioBackend *AudioBackend::getBackend(const std::string& slug)
{
	if (backends == nullptr)
		return nullptr;
	if (slug == "auto")
	{
		// Prefer sdl2 if available and avoid the null driver
		AudioBackend *sdlBackend = nullptr;
		AudioBackend *autoBackend = nullptr;
		for (auto backend : *backends)
		{
			if (backend->slug == "sdl2")
				sdlBackend = backend;
			if (backend->slug != "null" && autoBackend == nullptr)
				autoBackend = backend;
		}
		if (sdlBackend != nullptr)
			autoBackend = sdlBackend;
		if (autoBackend == nullptr)
			autoBackend = backends->front();
		INFO_LOG(AUDIO, "Auto-selected audio backend \"%s\" (%s).", autoBackend->slug.c_str(), autoBackend->getName().c_str());

		return autoBackend;
	}
	for (auto backend : *backends)
	{
		if (backend->slug == slug)
			return backend;
	}
	WARN_LOG(AUDIO, "WARNING: Audio backend \"%s\" not found!", slug.c_str());
	return nullptr;
}

#ifndef LIBRETRO
// GBCast fast-forward audio "Speed up": keeps 1 sample in <speed>, so the audio plays faster (and higher) like the
// game. The speed is the emulated audio time over the real time, measured every 0.1 s of emulated audio.
static bool keepFastForwardSample()
{
	using clock = std::chrono::steady_clock;
	static clock::time_point last;
	static u32 count;
	static float speed = 1.f;
	static float acc;
	if (++count >= 4410)
	{
		const clock::time_point now = clock::now();
		const float dt = std::chrono::duration<float>(now - last).count();
		// The first measure after fast-forward starts covers the time spent at normal speed: it comes out ~1x
		speed = 0.5f * speed + 0.5f * std::clamp(dt > 0.f ? 0.1f / dt : 1.f, 1.f, 16.f);
		last = now;
		count = 0;
	}
	acc += 1.f / speed;
	if (acc < 1.f)
		return false;
	acc -= 1.f;
	return true;
}
#endif

void WriteSample(s16 r, s16 l)
{
	bool wait = config::LimitFPS;
#ifndef LIBRETRO
	if (settings.input.fastForwardMode)
	{
		// Never wait for the audio output while fast-forwarding (it would hold the game at normal speed); "Default"
		// plays what fits at normal pitch, "Speed up" keeps fewer samples. "Mute" never gets here (sgc_if).
		wait = false;
		if (config::FastForwardAudio == 1 && !keepFastForwardSample())
			return;
	}
#endif
	Buffer[writePtr].r = r * config::AudioVolume.dbPower();
	Buffer[writePtr].l = l * config::AudioVolume.dbPower();

	if (++writePtr == SAMPLE_COUNT)
	{
		if (currentBackend != nullptr)
			currentBackend->push(Buffer, SAMPLE_COUNT, wait);
		writePtr = 0;
	}
}

void InitAudio()
{
	registerForEvents();
	TermAudio();

	std::string slug = config::AudioBackend;
	currentBackend = AudioBackend::getBackend(slug);
	if (currentBackend == nullptr && slug != "auto")
	{
		slug = "auto";
		currentBackend = AudioBackend::getBackend(slug);
	}
	if (currentBackend != nullptr)
	{
		INFO_LOG(AUDIO, "Initializing audio backend \"%s\" (%s)...", currentBackend->slug.c_str(), currentBackend->getName().c_str());
		if (!currentBackend->init())
		{
			currentBackend = nullptr;
			if (slug != "auto")
			{
				WARN_LOG(AUDIO, "Audio driver %s failed to initialize. Defaulting to 'auto'", slug.c_str());
				slug = "auto";
				currentBackend = AudioBackend::getBackend(slug);
				if (!currentBackend->init())
					currentBackend = nullptr;
			}
		}
	}

	if (currentBackend == nullptr)
	{
		WARN_LOG(AUDIO, "Running without audio!");
		return;
	}

	if (audio_recording_started)
	{
		// Restart recording
		audio_recording_started = false;
		StartAudioRecording(eight_khz);
	}
}

void TermAudio()
{
	if (currentBackend == nullptr)
		return;

	// Save recording state before stopping
	bool rec_started = audio_recording_started;
	StopAudioRecording();
	audio_recording_started = rec_started;
	currentBackend->term();
	INFO_LOG(AUDIO, "Terminating audio backend \"%s\" (%s)...", currentBackend->slug.c_str(), currentBackend->getName().c_str());
	currentBackend = nullptr;
}

void StartAudioRecording(bool eight_khz)
{
	::eight_khz = eight_khz;
	if (currentBackend != nullptr)
		audio_recording_started = currentBackend->initRecord(eight_khz ? 8000 : 11025);
	else
		// might be called between TermAudio/InitAudio
		audio_recording_started = true;
}

u32 RecordAudio(void *buffer, u32 samples)
{
	if (!audio_recording_started || currentBackend == nullptr)
		return 0;
	return currentBackend->record(buffer, samples);
}

void StopAudioRecording()
{
	// might be called between TermAudio/InitAudio
	if (audio_recording_started && currentBackend != nullptr)
		currentBackend->termRecord();
	audio_recording_started = false;
}

static void registerForEvents()
{
	static bool done;
	if (done)
		return;
	done = true;
	// Empty the audio buffer when loading a state or terminating the game
	const auto& callback = [](Event, void *) {
		writePtr = 0;
	};
	EventManager::listen(Event::Terminate, callback);
	EventManager::listen(Event::LoadState, callback);
}


