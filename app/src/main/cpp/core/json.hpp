// Licensed under the MIT License
//
// json.hpp — Minimal JSON parser/serializer (no external dependencies).

#ifndef ZBTCA_CORE_JSON_HPP
#define ZBTCA_CORE_JSON_HPP

#include <cstdint>
#include <map>
#include <sstream>
#include <string>
#include <string_view>
#include <vector>
#include <cmath>

namespace core {

    class JsonValue {
    public:
        enum class Type { Null, Bool, Number, String, Array, Object };

        JsonValue() = default;
        JsonValue(std::nullptr_t) {}
        JsonValue(bool v)  : m_type(Type::Bool),   m_bool(v) {}
        JsonValue(int v)   : m_type(Type::Number), m_num(static_cast<double>(v)) {}
        JsonValue(double v) : m_type(Type::Number), m_num(v) {}
        JsonValue(std::string const& v)   : m_type(Type::String), m_str(v) {}
        JsonValue(std::string&& v)        : m_type(Type::String), m_str(std::move(v)) {}
        JsonValue(const char* v)          : m_type(Type::String), m_str(v ? v : "") {}

        [[nodiscard]] Type type() const noexcept { return m_type; }
        [[nodiscard]] bool is_null()   const noexcept { return m_type == Type::Null; }
        [[nodiscard]] bool is_bool()    const noexcept { return m_type == Type::Bool; }
        [[nodiscard]] bool is_number()  const noexcept { return m_type == Type::Number; }
        [[nodiscard]] bool is_string()  const noexcept { return m_type == Type::String; }
        [[nodiscard]] bool is_array()   const noexcept { return m_type == Type::Array; }
        [[nodiscard]] bool is_object()  const noexcept { return m_type == Type::Object; }

        [[nodiscard]] std::string const& as_string() const { return m_str; }
        [[nodiscard]] double as_number() const { return m_num; }
        [[nodiscard]] int    as_int()    const { return static_cast<int>(m_num); }
        [[nodiscard]] bool as_bool() const { return m_bool; }

        [[nodiscard]] bool contains(std::string const& key) const {
            return m_type == Type::Object && m_obj.find(key) != m_obj.end();
        }
        JsonValue const& at(std::string const& key) const {
            static JsonValue null_val;
            if (m_type != Type::Object) return null_val;
            auto it = m_obj.find(key);
            return it != m_obj.end() ? it->second : null_val;
        }
        JsonValue& operator[](std::string const& key) {
            if (m_type != Type::Object) { m_type = Type::Object; }
            return m_obj[key];
        }

        [[nodiscard]] std::size_t size() const {
            if (m_type == Type::Array) return m_arr.size();
            if (m_type == Type::Object) return m_obj.size();
            return 0;
        }
        JsonValue const& at(std::size_t idx) const {
            static JsonValue null_val;
            if (m_type != Type::Array || idx >= m_arr.size()) return null_val;
            return m_arr[idx];
        }

        static JsonValue parse(std::string_view src) {
            JsonValue v;
            const char* p = src.data();
            const char* end = p + src.size();
            skip_ws(p, end);
            v = parse_value(p, end);
            return v;
        }

        [[nodiscard]] std::string dump(int indent = -1) const {
            std::ostringstream ss;
            write(ss, indent, 0);
            return ss.str();
        }

        std::map<std::string, JsonValue> const& object_items() const {
            static const std::map<std::string, JsonValue> empty;
            if (m_type != Type::Object) return empty;
            return m_obj;
        }

    private:
        Type m_type{Type::Null};
        bool m_bool{false};
        double m_num{0.0};
        std::string m_str;
        std::vector<JsonValue> m_arr;
        std::map<std::string, JsonValue> m_obj;

        static void skip_ws(const char*& p, const char* end) {
            while (p < end) {
                char c = *p;
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') ++p;
                else break;
            }
        }

        static JsonValue parse_value(const char*& p, const char* end) {
            skip_ws(p, end);
            if (p >= end) return JsonValue{};
            char c = *p;
            if (c == '{') return parse_object(p, end);
            if (c == '[') return parse_array(p, end);
            if (c == '"') return parse_string(p, end);
            if (c == 't' || c == 'f') return parse_bool(p, end);
            if (c == 'n') return parse_null(p, end);
            return parse_number(p, end);
        }

        static JsonValue parse_object(const char*& p, const char* end) {
            JsonValue v;
            v.m_type = Type::Object;
            ++p;
            skip_ws(p, end);
            if (p < end && *p == '}') { ++p; return v; }
            while (p < end) {
                skip_ws(p, end);
                if (p >= end || *p != '"') break;
                auto key = parse_raw_string(p, end);
                skip_ws(p, end);
                if (p >= end || *p != ':') break;
                ++p;
                skip_ws(p, end);
                v.m_obj[std::string(key)] = parse_value(p, end);
                skip_ws(p, end);
                if (p < end && *p == ',') { ++p; continue; }
                if (p < end && *p == '}') { ++p; break; }
                break;
            }
            return v;
        }

        static JsonValue parse_array(const char*& p, const char* end) {
            JsonValue v;
            v.m_type = Type::Array;
            ++p;
            skip_ws(p, end);
            if (p < end && *p == ']') { ++p; return v; }
            while (p < end) {
                skip_ws(p, end);
                v.m_arr.push_back(parse_value(p, end));
                skip_ws(p, end);
                if (p < end && *p == ',') { ++p; continue; }
                if (p < end && *p == ']') { ++p; break; }
                break;
            }
            return v;
        }

        static JsonValue parse_string(const char*& p, const char* end) {
            JsonValue v;
            v.m_type = Type::String;
            v.m_str = parse_raw_string(p, end);
            return v;
        }

        static std::string parse_raw_string(const char*& p, const char* end) {
            std::string s;
            ++p;
            while (p < end) {
                char c = *p;
                if (c == '"') { ++p; break; }
                if (c == '\\' && p + 1 < end) {
                    ++p;
                    char e = *p++;
                    switch (e) {
                        case '"':  s.push_back('"'); break;
                        case '\\': s.push_back('\\'); break;
                        case '/':  s.push_back('/'); break;
                        case 'n':  s.push_back('\n'); break;
                        case 't':  s.push_back('\t'); break;
                        case 'r':  s.push_back('\r'); break;
                        case 'b':  s.push_back('\b'); break;
                        case 'f':  s.push_back('\f'); break;
                        case 'u': {
                            if (p + 4 <= end) {
                                unsigned cp = 0;
                                for (int i = 0; i < 4; ++i) {
                                    cp <<= 4;
                                    char h = p[i];
                                    if (h >= '0' && h <= '9') cp |= (h - '0');
                                    else if (h >= 'a' && h <= 'f') cp |= (h - 'a' + 10);
                                    else if (h >= 'A' && h <= 'F') cp |= (h - 'A' + 10);
                                }
                                p += 4;
                                if (cp < 0x80) {
                                    s.push_back(static_cast<char>(cp));
                                } else if (cp < 0x800) {
                                    s.push_back(static_cast<char>(0xC0 | (cp >> 6)));
                                    s.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                                } else {
                                    s.push_back(static_cast<char>(0xE0 | (cp >> 12)));
                                    s.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
                                    s.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                                }
                            }
                            break;
                        }
                        default: s.push_back(e); break;
                    }
                } else {
                    s.push_back(c);
                    ++p;
                }
            }
            return s;
        }

        static JsonValue parse_number(const char*& p, const char* end) {
            JsonValue v;
            v.m_type = Type::Number;
            const char* start = p;
            if (p < end && (*p == '-' || *p == '+')) ++p;
            while (p < end) {
                char c = *p;
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E'
                    || c == '+' || c == '-') { ++p; }
                else break;
            }
            std::string num_str(start, p - start);
            try { v.m_num = std::stod(num_str); }
            catch (...) { v.m_num = 0.0; }
            return v;
        }

        static JsonValue parse_bool(const char*& p, const char* end) {
            JsonValue v;
            v.m_type = Type::Bool;
            if (end - p >= 4 && p[0] == 't' && p[1] == 'r' && p[2] == 'u' && p[3] == 'e') {
                v.m_bool = true; p += 4;
            } else if (end - p >= 5 && p[0] == 'f' && p[1] == 'a' && p[2] == 'l'
                       && p[3] == 's' && p[4] == 'e') {
                v.m_bool = false; p += 5;
            }
            return v;
        }

        static JsonValue parse_null(const char*& p, const char* end) {
            JsonValue v;
            v.m_type = Type::Null;
            if (end - p >= 4 && p[0] == 'n' && p[1] == 'u' && p[2] == 'l' && p[3] == 'l') {
                p += 4;
            }
            return v;
        }

        void write(std::ostringstream& ss, int indent, int level) const {
            switch (m_type) {
                case Type::Null:   ss << "null"; break;
                case Type::Bool:   ss << (m_bool ? "true" : "false"); break;
                case Type::Number: {
                    double intpart;
                    if (std::modf(m_num, &intpart) == 0.0
                        && m_num >= -2147483648.0 && m_num <= 2147483647.0) {
                        ss << static_cast<long long>(m_num);
                    } else {
                        ss << m_num;
                    }
                    break;
                }
                case Type::String: write_string(ss, m_str); break;
                case Type::Array: {
                    ss << '[';
                    for (std::size_t i = 0; i < m_arr.size(); ++i) {
                        if (i > 0) ss << ',';
                        if (indent >= 0) { ss << '\n'; write_indent(ss, indent, level + 1); }
                        m_arr[i].write(ss, indent, level + 1);
                    }
                    if (indent >= 0 && !m_arr.empty()) {
                        ss << '\n'; write_indent(ss, indent, level);
                    }
                    ss << ']';
                    break;
                }
                case Type::Object: {
                    ss << '{';
                    bool first = true;
                    for (auto const& [k, val] : m_obj) {
                        if (!first) ss << ',';
                        first = false;
                        if (indent >= 0) { ss << '\n'; write_indent(ss, indent, level + 1); }
                        write_string(ss, k);
                        ss << (indent >= 0 ? ": " : ":");
                        val.write(ss, indent, level + 1);
                    }
                    if (indent >= 0 && !m_obj.empty()) {
                        ss << '\n'; write_indent(ss, indent, level);
                    }
                    ss << '}';
                    break;
                }
            }
        }

        static void write_string(std::ostringstream& ss, std::string const& s) {
            ss << '"';
            for (unsigned char c : s) {
                switch (c) {
                    case '"':  ss << "\\\""; break;
                    case '\\': ss << "\\\\"; break;
                    case '\n': ss << "\\n"; break;
                    case '\t': ss << "\\t"; break;
                    case '\r': ss << "\\r"; break;
                    case '\b': ss << "\\b"; break;
                    case '\f': ss << "\\f"; break;
                    default:
                        if (c < 0x20) {
                            unsigned int uc = c;
                            ss << "\\u"
                               << "0123456789abcdef"[(uc >> 12) & 0xF]
                               << "0123456789abcdef"[(uc >> 8) & 0xF]
                               << "0123456789abcdef"[(uc >> 4) & 0xF]
                               << "0123456789abcdef"[uc & 0xF];
                        } else {
                            ss << static_cast<char>(c);
                        }
                        break;
                }
            }
            ss << '"';
        }

        static void write_indent(std::ostringstream& ss, int indent, int level) {
            for (int i = 0; i < indent * level; ++i) ss << ' ';
        }
    };

} // namespace core

#endif // ZBTCA_CORE_JSON_HPP