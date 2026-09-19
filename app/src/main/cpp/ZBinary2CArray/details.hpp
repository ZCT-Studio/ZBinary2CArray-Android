// Licensed under the MIT License

//
// Created by wanjiangzhi on 2026/9/17.
//

#ifndef ZBINARY2CARRAY_DETAILS_HPP
#define ZBINARY2CARRAY_DETAILS_HPP

#define ZBINARY2CARRAY_VERSION 10001
#define ZBINARY2CARRAY_VERSION_NAME "v1.0.1"

#ifdef _WIN32
#include <windows.h>
#endif
#include <ostream>
#include <cstdlib>
#include <array>
#include <string>
#include <filesystem>
#include <format>

#ifndef NDEBUG
#include <iostream>

#define ZBTCA_OUTPUT_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::cerr << "Assertion failed: " << #cond << "\n" \
            << "Message: " << msg << "\n" \
            << "File: " << __FILE__ << ", Line: " << __LINE__ << std::endl; \
            std::abort(); \
        } \
    } while (0)
#else
#include "response.hpp"
#define ZBTCA_OUTPUT_ASSERT(cond, msg) \
        do { \
            if (!(cond)) { \
               return ::ZBTCA_Response(msg, false); \
            } \
        } while (0)
#endif

namespace ZBTCA_Details {
    namespace fs = std::filesystem;

    inline std::string ReplaceInvalidChar(const std::string& input) {
        std::string result;
        result.reserve(input.size());

        static constexpr std::array replace_list = {
            '~', '`', '!', '@', '#', '$', '%', '^', '&', '*',
            '(', ')', '-', '+', '=', '{', '}', '[', ']', '\\',
            '|', ':', ';', '"', '\'', '<', '>', '?', ',', '.',
            '/'
        };

        for (const char c : input) {
            bool should_replace = false;
            for (const char bad_char : replace_list) {
                if (c == bad_char) {
                    should_replace = true;
                    break;
                }
            }

            if (should_replace) result += '_';
            else result += c;
        }

        if (!result.empty() && result[0] >= '0' && result[0] <= '9') {
            return "_" + result;
        }

        return result;
    }

    inline bool FileHasNotSuffix(const fs::path& path) {
        const auto& ext = path.extension();
        return ext.empty() || ext == ".";
    }

    inline std::string GetENV(const std::string& key) {
        std::string result;
        #ifdef _MSC_VER
        char* value = nullptr;
        if (_dupenv_s(&value, nullptr, key.c_str()) == 0 && value) {
            result = std::string(value);
            free(value);
        } else return {};
        #else
        if (const char* value = std::getenv(key.c_str())) result = value;
        else return {};
        #endif
        return result;
    }

    inline std::string GetSysUserName() {
        #ifdef _WIN32
        wchar_t username[256];
        DWORD size = std::size(username);

        if (!GetUserNameW(username, &size))
            return {};
        const int size_needed = WideCharToMultiByte(
            65001,
            0,
            username,
            static_cast<int>(size - 1),
            nullptr,
            0,
            nullptr,
            nullptr
        );

        if (size_needed <= 0)
            return {};

        std::string u8username(size_needed, 0);
        const int result = WideCharToMultiByte(
            65001,
            0,
            username,
            static_cast<int>(size - 1),
            &u8username[0],
            size_needed,
            nullptr,
            nullptr
        );

        if (result <= 0) return {};
        return u8username;
        #else
        std::string username;
        username = GetENV("USER");
        if (username.empty()) {
            username = GetENV("USERNAME");
        }
        return username;
        #endif
    }

    struct OStreamStateGuard {
        std::ostream& os;
        std::ios::fmtflags flags;
        char fill;

        explicit OStreamStateGuard(std::ostream& os)
            : os(os), flags(os.flags()), fill(os.fill()) {}

        ~OStreamStateGuard() {
            os.flags(flags);
            os.fill(fill);
        }
    };

    template <typename T>
    std::size_t WriteOutBin(
        std::ostream& ofs,
        const std::uint8_t* bin_ptr,
        const std::size_t num_bytes,
        const bool more_tidy,
        const std::size_t nums_per_line,
        const char* tab = "  ",
        const char* space = " ",
        const char* endl = "\n",
        const char* comma = ","
    ) {
        OStreamStateGuard Ofs_Guard(ofs);

        const T* rawData = reinterpret_cast<const T*>(bin_ptr);
        constexpr bool is_u8 = std::is_same_v<T, std::uint8_t>;
        const std::size_t numElements = num_bytes / sizeof(T);
        for (std::size_t i = 0; i < numElements; ++i) {
            if (more_tidy) {
                if (i % nums_per_line == 0) ofs << tab;
                ofs << "0x"
                    << std::hex << std::uppercase
                    << std::setw(sizeof(T) * 2)
                    << std::setfill('0')
                    << (is_u8 ? static_cast<std::uint32_t>(rawData[i]) : rawData[i]);

                if (i != numElements - 1) ofs << comma;
                if ((i + 1) % nums_per_line == 0) ofs << endl;
                else ofs << space;
            } else {
                if constexpr (is_u8)
                    ofs << static_cast<std::int16_t>(rawData[i]); // 宽化
                else ofs << rawData[i];

                if (i != numElements - 1) ofs << comma;
            }
        }

        return numElements;
    }
    namespace fmt {
        using std::format;
    }
}

#endif //ZBINARY2CARRAY_DETAILS_HPP