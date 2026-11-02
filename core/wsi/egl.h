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
#pragma once
#include "gl_context.h"

#if !defined(USE_SDL) && !defined(LIBRETRO) && (defined(__ANDROID__) || defined(SUPPORT_X11))
#define USE_EGL
#include <glad/egl.h>
#include <cstdint>
#include <vector>

class EGLGraphicsContext : public GLGraphicsContext
{
public:
	~EGLGraphicsContext() { term(); }

	void swap() override;
	static void Create(void *window, void *display);

protected:
	EGLGraphicsContext(void *window, void *display);
	bool init();
	void term();
	bool makeCurrent();
	void changeSwapInterval();
#ifdef __ANDROID__
	// Shaders and filters of the kit's presenter: the game renders off-screen and each frame goes to the presenter
	bool presenterActive() const;
	void presentThroughPresenter();
	void applyPresenterMode(bool on);
#endif

	EGLDisplay display = EGL_NO_DISPLAY;
	EGLSurface surface = EGL_NO_SURFACE;
	EGLContext context = EGL_NO_CONTEXT;
	bool swapOnVSync = false;
	int maxSwapInterval = 1;
	int currentSwapInterval = 1;
#ifdef __ANDROID__
	std::vector<uint32_t> presenterPixels;
	EGLConfig eglConfig = nullptr;
	bool presenterOn = false;
	int presenterShader = -1;
#endif
};
#endif
