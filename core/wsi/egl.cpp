#include "gbcast_skip.h"
/*
    Created on: Oct 18, 2019

	Copyright 2019 flyinghead

	This file is part of Flycast.

    Flycast is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    Flycast is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Flycast.  If not, see <https://www.gnu.org/licenses/>.
*/
#include "egl.h"

#ifdef USE_EGL
#include "types.h"
#include "cfg/option.h"

#ifdef __ANDROID__
#include <android/native_window.h> // requires ndk r5 or newer
#include "gl_renderer.hpp"
#include <algorithm>
#include <chrono>
#include <thread>
#endif

#ifndef SWAPPY
void EGLGraphicsContext::Create(void *window, void *display)
{
	new EGLGraphicsContext(window, display);
}
#endif

EGLGraphicsContext::EGLGraphicsContext(void *window, void *display)
	: GLGraphicsContext(window, display)
{
	if (!init())
		throw FlycastException("OpenGL initialization failed");
}

bool EGLGraphicsContext::makeCurrent()
{
	if (surface == EGL_NO_SURFACE || context == EGL_NO_CONTEXT)
		return false;
	return eglMakeCurrent(display, surface, surface, context);
}

bool EGLGraphicsContext::init()
{
	int version = gladLoaderLoadEGL(EGL_NO_DISPLAY);
	if (version == 0 || eglGetDisplay == nullptr || eglInitialize == nullptr) {
		ERROR_LOG(RENDERER, "Failed to load libEGL.so");
		return false;
	}
	NOTICE_LOG(RENDERER, "EGL version %d.%d", GLAD_VERSION_MAJOR(version), GLAD_VERSION_MINOR(version));

	//try to get a display
	display = eglGetDisplay((EGLNativeDisplayType)display);

	//if failed, get the default display (this will not happen in win32)
	if (display == EGL_NO_DISPLAY)
		display = eglGetDisplay(EGL_DEFAULT_DISPLAY);

	// Initialise EGL
	EGLint maj, min;
	if (!eglInitialize(display, &maj, &min))
	{
		ERROR_LOG(RENDERER, "EGL Error: eglInitialize failed");
		return false;
	}

	gladLoaderLoadEGL(display);

	if (surface == EGL_NO_SURFACE)
	{
		EGLint pi32ConfigAttribs[]  = {
				EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
				EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
				EGL_RED_SIZE, 8,
				EGL_GREEN_SIZE, 8,
				EGL_BLUE_SIZE, 8,
				EGL_NONE
		};

		int num_config;

		EGLConfig config;
		if (!eglChooseConfig(display, pi32ConfigAttribs, &config, 1, &num_config) || (num_config != 1))
		{
			ERROR_LOG(RENDERER, "EGL Error: eglChooseConfig failed");
			return false;
		}
#ifdef __ANDROID__
		EGLint format;
		if (!eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &format))
		{
			ERROR_LOG(RENDERER, "eglGetConfigAttrib() returned error %x", eglGetError());
			return false;
		}
		ANativeWindow_setBuffersGeometry((ANativeWindow *)window, 0, 0, format);
#endif
#ifdef __ANDROID__
		eglConfig = config;
		presenterOn = presenterActive();
		if (presenterOn)
		{
			// The presenter draws into the window; the game renders into an off-screen surface of the same size
			ANativeWindow *nativeWindow = (ANativeWindow *)window;
			ANativeWindow_acquire(nativeWindow); // SetWindow takes ownership of one reference
			glr::SetWindow(nativeWindow);
			glr::SetOptions(1, config::PresenterShader);
			glr::SetColorCorrection(0);
			EGLint pbufferAttrs[] = { EGL_WIDTH, ANativeWindow_getWidth(nativeWindow), EGL_HEIGHT, ANativeWindow_getHeight(nativeWindow), EGL_NONE };
			surface = eglCreatePbufferSurface(display, config, pbufferAttrs);
		}
		else
#endif
		surface = eglCreateWindowSurface(display, config, (EGLNativeWindowType)window, nullptr);

		if (surface == EGL_NO_SURFACE)
		{
			ERROR_LOG(RENDERER, "EGL Error: eglCreateWindowSurface failed: %x", eglGetError());
			return false;
		}

#ifndef GLES
		bool try_full_gl = true;
		if (!eglBindAPI(EGL_OPENGL_API))
		{
			INFO_LOG(RENDERER, "eglBindAPI(EGL_OPENGL_API) failed: %x", eglGetError());
			try_full_gl = false;
		}
		if (try_full_gl)
		{
			EGLint contextAttrs[] = { EGL_CONTEXT_MAJOR_VERSION, 3,
									  EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL_CONTEXT_OPENGL_COMPATIBILITY_PROFILE_BIT,
									  EGL_NONE };
			context = eglCreateContext(display, config,  EGL_NO_CONTEXT, contextAttrs);
			if (context != EGL_NO_CONTEXT)
			{
				makeCurrent();
				if (!gladLoadGL((GLADloadfunc) eglGetProcAddress))
					ERROR_LOG(RENDERER, "gladLoadGL() failed");
			}
		}
#endif
		if (context == EGL_NO_CONTEXT)
		{
			if (!eglBindAPI(EGL_OPENGL_ES_API))
			{
				ERROR_LOG(RENDERER, "eglBindAPI() failed: %x", eglGetError());
				return false;
			}

			EGLint contextAttrs[] = { EGL_CONTEXT_CLIENT_VERSION, 2 , EGL_NONE };

			context = eglCreateContext(display, config,  EGL_NO_CONTEXT, contextAttrs);

			if (context == EGL_NO_CONTEXT)
			{
				ERROR_LOG(RENDERER, "eglCreateContext() failed: %x", eglGetError());
				return false;
			}

			makeCurrent();
			if (!gladLoadGLES2((GLADloadfunc) eglGetProcAddress))
				ERROR_LOG(RENDERER, "gladLoadGLES2() failed");
		}
		eglGetConfigAttrib(display, config, EGL_MAX_SWAP_INTERVAL, &maxSwapInterval);
	}

	if (!makeCurrent())
	{
		ERROR_LOG(RENDERER, "eglMakeCurrent() failed: %x", eglGetError());
		return false;
	}

	EGLint w, h;
	eglQuerySurface(display, surface, EGL_WIDTH, &w);
	eglQuerySurface(display, surface, EGL_HEIGHT, &h);
	NOTICE_LOG(RENDERER, "eglQuerySurface: %d - %d", w, h);

	settings.display.width = w;
	settings.display.height = h;

	changeSwapInterval();

	postInit();

	INFO_LOG(RENDERER, "EGL config: %p, %p, %p %dx%d", context, display, surface, w, h);
	return true;
}

void EGLGraphicsContext::term()
{
	preTerm();
#ifdef __ANDROID__
	if (presenterActive())
		glr::SetWindow(nullptr); // releases the window reference taken in init()
#endif

	if (display != EGL_NO_DISPLAY)
	{
		glFlush();
		glFinish();
		eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

		if (context != EGL_NO_CONTEXT)
			eglDestroyContext(display, context);

		if (surface != EGL_NO_SURFACE)
			eglDestroySurface(display, surface);

		eglTerminate(display);
	}

	context = EGL_NO_CONTEXT;
	surface = EGL_NO_SURFACE;
	display = EGL_NO_DISPLAY;
}

#ifdef __ANDROID__
bool EGLGraphicsContext::presenterActive() const
{
	// Bilinear is the direct renderer's default. Route frames through the shared presenter when the user selects
	// a post-process shader or a non-default output filter.
	return config::PresenterShader != 0;
}

// Moves the game between the window (plain output) and an off-screen surface (presenter). Runs on the render thread:
// the window is detached from the old owner before the new one takes it.
void EGLGraphicsContext::applyPresenterMode(bool on)
{
	eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context);
	eglDestroySurface(display, surface);
	surface = EGL_NO_SURFACE;
	ANativeWindow *nativeWindow = (ANativeWindow *)window;
	if (on)
	{
		ANativeWindow_acquire(nativeWindow); // SetWindow takes ownership of one reference
		glr::SetWindow(nativeWindow);
		EGLint pbufferAttrs[] = { EGL_WIDTH, ANativeWindow_getWidth(nativeWindow), EGL_HEIGHT, ANativeWindow_getHeight(nativeWindow), EGL_NONE };
		surface = eglCreatePbufferSurface(display, eglConfig, pbufferAttrs);
	}
	else
	{
		glr::SetWindow(nullptr); // the presenter releases the window once it has dropped its surface: retry until then
		for (int attempt = 0; attempt < 100 && surface == EGL_NO_SURFACE; attempt++)
		{
			surface = eglCreateWindowSurface(display, eglConfig, (EGLNativeWindowType)window, nullptr);
			if (surface == EGL_NO_SURFACE)
				std::this_thread::sleep_for(std::chrono::milliseconds(10));
		}
	}
	if (surface == EGL_NO_SURFACE)
		ERROR_LOG(RENDERER, "EGL Error: could not set up the %s surface: %x", on ? "off-screen" : "window", eglGetError());
	makeCurrent();
	presenterOn = on;
	presenterShader = -1; // sends the shader to the presenter on the next frame
}

// Reads the off-screen frame (OpenGL rows start at the bottom) and hands it to the presenter, top row first
void EGLGraphicsContext::presentThroughPresenter()
{
	if (config::PresenterShader != presenterShader)
	{
		presenterShader = config::PresenterShader;
		glr::SetOptions(1, presenterShader);
	}
	EGLint w, h;
	eglQuerySurface(display, surface, EGL_WIDTH, &w);
	eglQuerySurface(display, surface, EGL_HEIGHT, &h);
	presenterPixels.resize((size_t)w * h);
	glBindFramebuffer(GL_FRAMEBUFFER, 0);
	glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, presenterPixels.data());
	for (EGLint y = 0; y < h / 2; y++)
		std::swap_ranges(presenterPixels.begin() + (size_t)y * w, presenterPixels.begin() + (size_t)(y + 1) * w,
						 presenterPixels.begin() + (size_t)(h - 1 - y) * w);
	glr::Submit(presenterPixels.data(), w, h, 1);
}
#endif

void EGLGraphicsContext::swap()
{
	do_swap_automation();
	changeSwapInterval();
#ifdef __ANDROID__
	if (presenterActive() != presenterOn)
		applyPresenterMode(presenterActive());
	if (presenterOn)
	{
		presentThroughPresenter();
		return;
	}
#endif
	eglSwapBuffers(display, surface);
}

void EGLGraphicsContext::changeSwapInterval()
{
	if (!gameSwapIntervalChanged && swapOnVSync == (!(settings.input.fastForwardMode || gbcast::AutoSkipActive()) && config::VSync))
		return;
	swapOnVSync = (!(settings.input.fastForwardMode || gbcast::AutoSkipActive()) && config::VSync);
	gameSwapIntervalChanged = false;
	currentSwapInterval = 0;
	if (swapOnVSync)
	{
		if (settings.display.refreshRate > 60.f)
			currentSwapInterval = settings.display.refreshRate / 60.f * gameSwapInterval;
		else
			currentSwapInterval = gameSwapInterval;
		INFO_LOG(RENDERER, "Swap interval changed to %d (max %d)", currentSwapInterval, maxSwapInterval);
	}
	eglSwapInterval(display, std::min(currentSwapInterval, maxSwapInterval));
}
#endif // USE_EGL
