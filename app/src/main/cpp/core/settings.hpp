// Licensed under the MIT License
//
// settings.hpp — application settings model + JSON persistence.

#ifndef ZBTCA_CORE_SETTINGS_HPP
#define ZBTCA_CORE_SETTINGS_HPP

#include "json.hpp"

#include <filesystem>
#include <string>

namespace core {

    enum class ThemePreference : int {
        System = 0,
        Light  = 1,
        Dark   = 2,
    };

    struct AppSettings {
        std::string    language{ "en-US" };
        ThemePreference theme{ ThemePreference::System };
        bool           first_run{ true };
    };

    [[nodiscard]] AppSettings load_settings(std::filesystem::path const& path);
    void save_settings(AppSettings const& settings,
                       std::filesystem::path const& path);

} // namespace core

#endif // ZBTCA_CORE_SETTINGS_HPP