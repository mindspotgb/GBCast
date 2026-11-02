/*
	Copyright 2025 flyinghead

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
#include "settings.h"
#include "gui.h"
#include "wsi/context.h"

enum RenderAPI {
	OpenGL,
	Vulkan,
	DirectX9,
	DirectX11
};

void gui_settings_video()
{
	int renderApi;
	bool perPixel;
	switch (config::RendererType)
	{
	default:
	case RenderType::OpenGL:
		renderApi = OpenGL;
		perPixel = false;
		break;
	case RenderType::OpenGL_OIT:
		renderApi = OpenGL;
		perPixel = true;
		break;
	case RenderType::Vulkan:
		renderApi = Vulkan;
		perPixel = false;
		break;
	case RenderType::Vulkan_OIT:
		renderApi = Vulkan;
		perPixel = true;
		break;
	case RenderType::DirectX9:
		renderApi = DirectX9;
		perPixel = false;
		break;
	case RenderType::DirectX11:
		renderApi = DirectX11;
		perPixel = false;
		break;
	case RenderType::DirectX11_OIT:
		renderApi = DirectX11;
		perPixel = true;
		break;
	}

	constexpr int apiCount = 0
		#ifdef USE_VULKAN
			+ 1
		#endif
		#ifdef USE_DX9
			+ 1
		#endif
		#ifdef USE_OPENGL
			+ 1
		#endif
		#ifdef USE_DX11
			+ 1
		#endif
			;

    float innerSpacing = ImGui::GetStyle().ItemInnerSpacing.x;
	if (apiCount > 1)
	{
		header(T("Graphics API"));
		{
			ImGui::Columns(apiCount, "renderApi", false);
#ifdef USE_OPENGL
			ImGui::RadioButton("OpenGL", &renderApi, OpenGL);
			ImGui::NextColumn();
#endif
#ifdef USE_VULKAN
#ifdef __APPLE__
			ImGui::RadioButton("Vulkan (Metal)", &renderApi, Vulkan);
			ImGui::SameLine(0, innerSpacing);
			ShowHelpMarker(T("MoltenVK: An implementation of Vulkan that runs on Apple's Metal graphics framework"));
#else
			ImGui::RadioButton("Vulkan", &renderApi, Vulkan);
#endif // __APPLE__
			ImGui::NextColumn();
#endif
#ifdef USE_DX9
			{
				DisabledScope _(settings.platform.isNaomi2());
				ImGui::RadioButton("DirectX 9", &renderApi, DirectX9);
				ImGui::NextColumn();
			}
#endif
#ifdef USE_DX11
			ImGui::RadioButton("DirectX 11", &renderApi, DirectX11);
			ImGui::NextColumn();
#endif
			ImGui::Columns(1, nullptr, false);
	}
    }
    header(T("Transparent Sorting"));
    {
		const bool has_per_pixel = GraphicsContext::Instance()->hasPerPixel();
	int renderer = perPixel ? 2 : config::PerStripSorting ? 1 : 0;
	ImGui::Columns(has_per_pixel ? 3 : 2, "renderers", false);
	ImGui::RadioButton(T("Per Triangle"), &renderer, 0);
        ImGui::SameLine();
        ShowHelpMarker(T("Sorts each transparent polygon separately. This is usually the safest choice for correct overlap."));
	ImGui::NextColumn();
	ImGui::RadioButton(T("Per Strip"), &renderer, 1);
        ImGui::SameLine();
        ShowHelpMarker(T("Groups transparent polygons into strips before sorting. It can be faster, but some games may show incorrect overlap."));
        if (has_per_pixel)
        {
	ImGui::NextColumn();
	ImGui::RadioButton(T("Per Pixel"), &renderer, 2);
	ImGui::SameLine();
        ShowHelpMarker(T("Checks transparent order for each pixel for the most accurate result. This is the slowest option."));
        }
	ImGui::Columns(1, nullptr, false);
	switch (renderer)
	{
	case 0:
		perPixel = false;
		config::PerStripSorting.set(false);
		break;
	case 1:
		perPixel = false;
		config::PerStripSorting.set(true);
		break;
	case 2:
		perPixel = true;
		break;
	}
    }
	ImGui::Spacing();

    header(T("Rendering Options"));
    {
        const std::array<float, 13> scalings{ 0.5f, 1.f, 1.5f, 2.f, 2.5f, 3.f, 4.f, 4.5f, 5.f, 6.f, 7.f, 8.f, 9.f };
        const std::array<std::string, 13> scalingsText{ T("Half"), T("Native"), "x1.5", "x2", "x2.5", "x3", "x4", "x4.5", "x5", "x6", "x7", "x8", "x9" };
        std::array<int, scalings.size()> vres;
        std::array<std::string, scalings.size()> resLabels;
        u32 selected = 0;
        for (u32 i = 0; i < scalings.size(); i++)
        {
	vres[i] = scalings[i] * 480;
	if (vres[i] == config::RenderResolution)
		selected = i;
	if (!config::Widescreen)
		resLabels[i] = std::to_string((int)(scalings[i] * 640)) + "x" + std::to_string((int)(scalings[i] * 480));
	else
		resLabels[i] = std::to_string((int)(scalings[i] * 480 * 16 / 9)) + "x" + std::to_string((int)(scalings[i] * 480));
	resLabels[i] += " (" + scalingsText[i] + ")";
        }

        ImGui::PushItemWidth(ImGui::CalcItemWidth() - innerSpacing * 2.0f - ImGui::GetFrameHeight() * 2.0f);
        if (ImGui::BeginCombo("##Resolution", resLabels[selected].c_str(), ImGuiComboFlags_NoArrowButton))
        {
	for (u32 i = 0; i < scalings.size(); i++)
            {
                bool is_selected = vres[i] == config::RenderResolution;
                if (ImGui::Selectable(resLabels[i].c_str(), is_selected))
	config::RenderResolution = vres[i];
                if (is_selected)
                    ImGui::SetItemDefaultFocus();
            }
            ImGui::EndCombo();
        }
        ImGui::PopItemWidth();
        ImGui::SameLine(0, innerSpacing);

        if (ImGui::ArrowButton("##Decrease Res", ImGuiDir_Left))
        {
            if (selected > 0)
	config::RenderResolution = vres[selected - 1];
        }
        ImGui::SameLine(0, innerSpacing);
        if (ImGui::ArrowButton("##Increase Res", ImGuiDir_Right))
        {
            if (selected < vres.size() - 1)
	config::RenderResolution = vres[selected + 1];
        }
        ImGui::SameLine(0, innerSpacing);

        ImGui::Text("%s", T("Enhanced resolution"));
        ImGui::SameLine();
        ShowHelpMarker(T("Renders the game above its original resolution for sharper edges. Higher values use more GPU power; Native is the lightest setting."));
		#ifndef __ANDROID__
		OptionCheckbox(T("Integer scaling"), config::IntegerScale, T("Uses whole-number scale steps to keep pixels evenly sized. The picture may have larger borders."));
		#endif
		OptionCheckbox(T("Bilinear filtering"), config::LinearInterpolation, T("Smooths pixels when the image is resized. It looks softer than Nearest and has little performance cost."));
#ifndef TARGET_IPHONE
		OptionCheckbox(T("VSync"), config::VSync, T("Matches frame display to the screen refresh to reduce tearing. It can add a little input delay or limit speed."));
	ImGui::Indent();
		{
			DisabledScope scope(!config::VSync);
#ifdef __ANDROID__
			OptionCheckbox(T("Frame pacing"), config::FramePacing,
					T("Smooths uneven frame delivery by matching the console's original timing. It may add a small amount of waiting."));
#else
		if (isVulkan(config::RendererType))
			OptionCheckbox(T("Duplicate frames"), config::DupeFrames, T("Duplicate frames on high refresh rate monitors (120 Hz and higher)"));
#endif
	}
	ImGui::Unindent();
#endif
		OptionCheckbox(T("Show VMU in game"), config::FloatVMUs, T("Shows the memory card screen over the game. It has a small rendering cost."));
		OptionCheckbox(T("Framebuffer emulation"), config::EmulateFramebuffer,
				T("More accurately handles games that read the image back while drawing. It can be demanding and may conflict with upscaling or widescreen."));
		{
			DisabledScope scope(game_started);
			OptionCheckbox(T("Load custom textures"), config::CustomTextures,
					T("Replaces game images with files from an imported texture pack. It uses extra storage and memory; restart the game after changing packs."));
			ImGui::Indent();
			{
				DisabledScope scope(!config::CustomTextures.get());
				OptionCheckbox(T("Preload custom textures"), config::PreloadCustomTextures,
						T("Loads the pack before gameplay to avoid pauses when textures first appear. It uses more memory and can increase startup time."));
			}
			ImGui::Unindent();
		}
    }
	ImGui::Spacing();
    header(T("Aspect Ratio"));
    {
		OptionCheckbox(T("Widescreen"), config::Widescreen,
				T("Expands the game's view from 4:3 to widescreen so more of the scene is visible. Some games may show glitches at the edges."));
		{
			DisabledScope scope(!config::Widescreen || config::IntegerScale);

			ImGui::Indent();
			OptionCheckbox(T("Extended screen"), config::SuperWidescreen,
					T("Fills displays wider than 16:9 by cropping the sides. This can remove black bars but may hide game content."));
			ImGui::Unindent();
	}
		OptionCheckbox(T("Widescreen hack"), config::WidescreenGameHacks,
				T("Uses built-in widescreen codes in supported games. It can show more of the scene, but may cause visual glitches."));
		OptionSlider(T("Horizontal image stretch"), config::ScreenStretching, 100, 250,
				T("Stretches the picture sideways. Higher values distort shapes and do not improve emulation speed."), "%d%%");
		#ifndef __ANDROID__
		OptionCheckbox(T("Rotate Screen 90°"), config::Rotate90, T("Rotate the screen 90° counterclockwise"));
		#endif
    }
	if (perPixel)
	{
		ImGui::Spacing();
		header(T("Per Pixel Settings"));

		const std::array<int64_t, 4> bufSizes{ 512_MB, 1_GB, 2_GB, 4_GB };
		const std::array<std::string, 4> bufSizesText{ T("512 MB"), T("1 GB"), T("2 GB"), T("4 GB") };
        ImGui::PushItemWidth(ImGui::CalcItemWidth() - innerSpacing * 2.0f - ImGui::GetFrameHeight() * 2.0f);
		u32 selected = 0;
		for (; selected < bufSizes.size(); selected++)
			if (bufSizes[selected] == config::PixelBufferSize)
				break;
		if (selected == bufSizes.size())
			selected = 0;
		if (ImGui::BeginCombo("##PixelBuffer", bufSizesText[selected].c_str(), ImGuiComboFlags_NoArrowButton))
		{
			for (u32 i = 0; i < bufSizes.size(); i++)
			{
				bool is_selected = i == selected;
				if (ImGui::Selectable(bufSizesText[i].c_str(), is_selected))
					config::PixelBufferSize = bufSizes[i];
				if (is_selected) {
					ImGui::SetItemDefaultFocus();
					selected = i;
				}
			}
			ImGui::EndCombo();
		}
        ImGui::PopItemWidth();
		ImGui::SameLine(0, innerSpacing);

		if (ImGui::ArrowButton("##Decrease BufSize", ImGuiDir_Left))
		{
			if (selected > 0)
				config::PixelBufferSize = bufSizes[selected - 1];
		}
		ImGui::SameLine(0, innerSpacing);
		if (ImGui::ArrowButton("##Increase BufSize", ImGuiDir_Right))
		{
			if (selected < bufSizes.size() - 1)
				config::PixelBufferSize = bufSizes[selected + 1];
		}
		ImGui::SameLine(0, innerSpacing);

        ImGui::Text("%s", T("Pixel Buffer Size"));
        ImGui::SameLine();
        ShowHelpMarker(T("Memory reserved for per-pixel transparency sorting. Increase it only if a complex scene is cut off; larger buffers use more memory."));

        OptionSlider(T("Maximum transparent layers"), config::PerPixelLayers, 8, 128,
				T("Sets how many overlapping transparent effects can be drawn. Raise it if effects disappear; lower values may be faster."));
	}
	ImGui::Spacing();
    header(T("Performance"));
    {
	ImGui::Text("%s", T("Auto frame skip:"));
	ImGui::Columns(3, "autoskip", false);
		OptionRadioButton(T("Off"), config::AutoSkipFrame, 0, T("Always draw every frame. This looks smooth but does not reduce graphics work."));
	ImGui::NextColumn();
	OptionRadioButton(T("Normal"), config::AutoSkipFrame, 1, T("Skips drawing when both graphics and emulation are behind. Motion may look less smooth; audio timing stays normal."));
	ImGui::NextColumn();
	OptionRadioButton(T("Max"), config::AutoSkipFrame, 2, T("Also skips drawing when the GPU is slow even if the emulated CPU is keeping up. Motion may look less smooth; audio timing stays normal."));
	ImGui::Columns(1, nullptr, false);

	OptionArrowButtons(T("Fixed frame skip"), config::SkipFrame, 0, 6,
			T("Forces a set number of frames to be skipped. This can help when graphics are the bottleneck, but makes motion less smooth and cannot speed up a CPU-limited game."));
	OptionCheckbox(T("Shadows"), config::ModifierVolumes,
			T("Draws special shadow effects used by some games. Disabling it may remove shadows and can slightly reduce graphics work."));
	OptionCheckbox(T("Fog effects"), config::Fog, T("Keeps distance haze and fog used by some games. Turning it off changes the picture and may save a little GPU time."));
    }
    ImGui::Spacing();
	header(T("Advanced"));
    {
	OptionCheckbox(T("Delay frame swap"), config::DelayFrameSwapping,
			T("Waits before showing a frame to fix flicker or broken video in some games. It can reduce speed on weaker devices."));
	OptionCheckbox(T("Fix texture edges when upscaling"), config::FixUpscaleBleedingEdge,
			T("Prevents neighboring texture pixels from leaking into enlarged images. If 2D pixels warp, turn this off for that game."));
	OptionCheckbox(T("Accurate depth interpolation"), config::NativeDepthInterpolation,
			T("Uses a more accurate depth calculation to fix visual corruption on some graphics cards. It may cost performance; leave it off unless needed."));
	OptionCheckbox(T("Copy rendered textures to memory"), config::RenderToTextureBuffer,
			T("Copies graphics back into console memory for games that reuse the image as a texture. More accurate, but slower."));
		const std::array<int, 5> aniso{ 1, 2, 4, 8, 16 };
        const std::array<std::string, 5> anisoText{ T("Disabled"), "2x", "4x", "8x", "16x" };
        u32 afSelected = 0;
        for (u32 i = 0; i < aniso.size(); i++)
        {
	if (aniso[i] == config::AnisotropicFiltering)
		afSelected = i;
        }

        ImGui::PushItemWidth(ImGui::CalcItemWidth() - innerSpacing * 2.0f - ImGui::GetFrameHeight() * 2.0f);
        if (ImGui::BeginCombo("##Anisotropic Filtering", anisoText[afSelected].c_str(), ImGuiComboFlags_NoArrowButton))
        {
	for (u32 i = 0; i < aniso.size(); i++)
            {
                bool is_selected = aniso[i] == config::AnisotropicFiltering;
                if (ImGui::Selectable(anisoText[i].c_str(), is_selected))
	config::AnisotropicFiltering = aniso[i];
                if (is_selected)
                    ImGui::SetItemDefaultFocus();
            }
            ImGui::EndCombo();
        }
        ImGui::PopItemWidth();
        ImGui::SameLine(0, innerSpacing);

        if (ImGui::ArrowButton("##Decrease Anisotropic Filtering", ImGuiDir_Left))
        {
            if (afSelected > 0)
	config::AnisotropicFiltering = aniso[afSelected - 1];
        }
        ImGui::SameLine(0, innerSpacing);
        if (ImGui::ArrowButton("##Increase Anisotropic Filtering", ImGuiDir_Right))
        {
            if (afSelected < aniso.size() - 1)
	config::AnisotropicFiltering = aniso[afSelected + 1];
        }
        ImGui::SameLine(0, innerSpacing);

        ImGui::Text("%s", T("Anisotropic Filtering"));
        ImGui::SameLine();
        ShowHelpMarker(T("Higher values keep textures sharper when viewed at an angle. They use more GPU time and are visible only in games that use mipmaps."));

	ImGui::Text("%s", T("Filter:"));
	// GBFrame exposes only Nearest and Bilinear; migrate old per-game-default values to the kit default.
	if (config::TextureFiltering == 0)
		config::TextureFiltering = 2;
	ImGui::Columns(2, "textureFiltering", false);
	OptionRadioButton(T("Force Nearest"), config::TextureFiltering, 1, T("Keeps pixel edges sharp. Some games may show visual glitches; performance is usually unchanged."));
	ImGui::NextColumn();
	OptionRadioButton(T("Force Bilinear"), config::TextureFiltering, 2, T("Smooths game textures but makes pixels softer. Some games may show visual glitches; performance is usually unchanged."));
	ImGui::Columns(1, nullptr, false);

		OptionCheckbox(T("Show FPS counter"), config::ShowFPS, T("Shows the current frames per second over the game. The overlay has a small rendering cost."));
    }
#ifdef VIDEO_ROUTING
	ImGui::Spacing();
#ifdef __APPLE__
	header(T("Video Routing (Syphon)"));
#elif defined(_WIN32)
	if (renderApi == OpenGL || renderApi == DirectX11)
		header(T("Video Routing (Spout)"));
	else
		header(T("Video Routing (Only available with OpenGL or DirectX 11)"));
#endif
	{
#ifdef _WIN32
		DisabledScope scope(!(renderApi == OpenGL || renderApi == DirectX11));
#endif
		OptionCheckbox(T("Send video content to another program"), config::VideoRouting,
				T("e.g. Route GPU texture to OBS Studio directly instead of using CPU intensive Display/Window Capture"));

		{
			DisabledScope scope(!config::VideoRouting);
			OptionCheckbox(T("Scale down before sending"), config::VideoRoutingScale, T("Could increase performance when sharing a smaller texture, YMMV"));
			{
				DisabledScope scope(!config::VideoRoutingScale);
				static int vres = config::VideoRoutingVRes;
				if (ImGui::InputInt(T("Output vertical resolution"), &vres))
				{
					config::VideoRoutingVRes = vres;
				}
			}
			ImGui::Text(T("Output texture size: %d x %d"), config::VideoRoutingScale ? config::VideoRoutingVRes * settings.display.width / settings.display.height : settings.display.width, config::VideoRoutingScale ? config::VideoRoutingVRes : settings.display.height);
		}
	}
#endif

    switch (renderApi)
    {
    case OpenGL:
	config::RendererType = perPixel ? RenderType::OpenGL_OIT : RenderType::OpenGL;
	break;
    case Vulkan:
	config::RendererType = perPixel ? RenderType::Vulkan_OIT : RenderType::Vulkan;
	break;
    case DirectX9:
	config::RendererType = RenderType::DirectX9;
	break;
    case DirectX11:
	config::RendererType = perPixel ? RenderType::DirectX11_OIT : RenderType::DirectX11;
	break;
    }
}
