// Licensed under the MIT License
//
// i18n_manager.cpp — implementation of the singleton I18nManager.

#include "i18n_manager.hpp"

namespace core {

    I18nManager& I18nManager::instance() {
        static I18nManager inst;
        return inst;
    }

    void I18nManager::load_language(std::string_view tag,
                                     JsonValue const& data) {
        std::unordered_map<std::string, std::string> flat;
        flatten(data, "", flat);
        std::lock_guard<std::mutex> lk(m_mutex);
        m_tables[std::string(tag)] = std::move(flat);
    }

    void I18nManager::set_language(std::string const& tag) {
        std::vector<std::function<void(std::string const&)>> listeners;
        {
            std::lock_guard<std::mutex> lk(m_mutex);
            m_current_tag = tag;
            listeners = m_listeners;
        }
        for (auto const& fn : listeners) {
            try { fn(tag); } catch (...) {}
        }
    }

    std::string I18nManager::current_language() const noexcept {
        std::lock_guard<std::mutex> lk(m_mutex);
        return m_current_tag;
    }

    std::string I18nManager::tr(std::string const& key) const {
        std::lock_guard<std::mutex> lk(m_mutex);
        auto it = m_tables.find(m_current_tag);
        if (it != m_tables.end()) {
            auto val = it->second.find(key);
            if (val != it->second.end()) return val->second;
        }
        if (m_current_tag != "en-US") {
            auto fb = m_tables.find("en-US");
            if (fb != m_tables.end()) {
                auto val = fb->second.find(key);
                if (val != fb->second.end()) return val->second;
            }
        }
        return key;
    }

    std::string I18nManager::tr(std::string const& key,
                                 std::vector<std::string> const& args) const {
        std::string fmt = tr(key);
        for (std::size_t i = 0; i < args.size(); ++i) {
            std::string placeholder = "{" + std::to_string(i) + "}";
            std::string::size_type pos = 0;
            while ((pos = fmt.find(placeholder, pos)) != std::string::npos) {
                fmt.replace(pos, placeholder.size(), args[i]);
                pos += args[i].size();
            }
        }
        return fmt;
    }

    void I18nManager::add_language_changed_listener(
        std::function<void(std::string const&)> fn) {
        std::lock_guard<std::mutex> lk(m_mutex);
        m_listeners.push_back(std::move(fn));
    }

    void I18nManager::flatten(JsonValue const& v,
                               std::string const& prefix,
                               std::unordered_map<std::string, std::string>& out) const {
        if (!v.is_object()) {
            if (v.is_string()) {
                out[prefix] = v.as_string();
            }
            return;
        }
        for (auto const& [k, val] : v.object_items()) {
            std::string full_key = prefix.empty() ? k : (prefix + "." + k);
            if (val.is_object()) {
                flatten(val, full_key, out);
            } else if (val.is_string()) {
                out[full_key] = val.as_string();
            }
        }
    }

} // namespace core