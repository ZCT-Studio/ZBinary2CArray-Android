// Licensed under the MIT License
//
// i18n_manager.hpp — singleton internationalization manager.

#ifndef ZBTCA_CORE_I18N_MANAGER_HPP
#define ZBTCA_CORE_I18N_MANAGER_HPP

#include "json.hpp"

#include <functional>
#include <mutex>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace core {

    class I18nManager {
    public:
        static I18nManager& instance();

        void load_language(std::string_view tag, JsonValue const& data);
        void set_language(std::string const& tag);
        [[nodiscard]] std::string current_language() const noexcept;
        [[nodiscard]] std::string tr(std::string const& key) const;
        [[nodiscard]] std::string tr(std::string const& key,
                                     std::vector<std::string> const& args) const;
        void add_language_changed_listener(
            std::function<void(std::string const&)> fn);

    private:
        I18nManager() = default;
        I18nManager(I18nManager const&)            = delete;
        I18nManager& operator=(I18nManager const&)  = delete;

        void flatten(JsonValue const& v,
                     std::string const& prefix,
                     std::unordered_map<std::string, std::string>& out) const;

        mutable std::mutex m_mutex;
        std::string m_current_tag{ "en-US" };
        std::unordered_map<std::string,
                           std::unordered_map<std::string, std::string>> m_tables;
        std::vector<std::function<void(std::string const&)>> m_listeners;
    };

} // namespace core

#endif // ZBTCA_CORE_I18N_MANAGER_HPP