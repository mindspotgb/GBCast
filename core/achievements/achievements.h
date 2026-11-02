/*
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
#include "types.h"
#include <functional>
#include <future>
#include <vector>

namespace achievements
{
#ifdef USE_RACHIEVEMENTS

struct Game
{
	std::string image; // Existing local cache path used by Flycast's native UI.
	std::string title;
	u32 unlockedAchievements;
	u32 totalAchievements;
	u32 points;
	u32 totalPoints;
	std::string imageUrl;
	bool hardcore = false;
};

struct User
{
	std::string username;
	std::string token;
	std::string avatarUrl;
	u32 score = 0;
	u32 softcoreScore = 0;
};

struct Achievement
{
	Achievement() = default;
	Achievement(const std::string& image, const std::string& title, const std::string& description, const std::string& category, const std::string& status)
		: image(image), title(title), description(description), category(category), status(status) {}
	std::string image; // Existing local cache path used by Flycast's native UI.
	std::string title;
	std::string description;
	std::string category;
	std::string status;
	u32 id = 0;
	u32 points = 0;
	bool unlocked = false;
	std::string imageUrl;
	std::string lockedImageUrl;
	std::string progress;
};

bool init();
std::string takeOverlayEvents();
void term();
std::future<void> login(const char *username, const char *password);
void loginWithToken(const char *username, const char *token);
void logout();
void setHardcore(bool enabled);
bool isHardcore();
bool isLoginPending();
std::string lastLoginError();
User getCurrentUser();
bool isLoggedOn();
bool isActive();
Game getCurrentGame();
std::vector<Achievement> getAchievementList();
bool canPause();
void setHostOverride(const std::string& host);

// Optional platform transport. Android uses this to route rcheevos through GBFrame's
// offline-aware HTTP layer; other platforms keep the native HTTP implementation.
using HttpResponse = std::function<void(int status, std::vector<u8> body)>;
using HttpRequestHandler = std::function<bool(const std::string& url, const std::string* postData,
		const std::string* contentType, HttpResponse response)>;
void setHttpRequestHandler(HttpRequestHandler handler);

#else

static inline bool isActive() {
	return false;
}
static inline bool canPause() {
	return true;
}

#endif

void serialize(Serializer& ser);
void deserialize(Deserializer& deser);

}
