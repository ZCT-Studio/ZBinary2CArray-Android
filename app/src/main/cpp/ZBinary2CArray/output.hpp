// Licensed under the MIT License

//
// Created by wanjiangzhi on 2026/9/17.
//

#ifndef ZBINARY2CARRAY_OUTPUT_HPP
#define ZBINARY2CARRAY_OUTPUT_HPP

#include "response.hpp"
#include "types.hpp"
#include "bin.hpp"
#include "details.hpp"
#include <string>
#include <sstream>
#include <cstdlib>
#include <format>


class ZBTCA_Output {
protected:
    const ZBTCA_Bin& m_bin;
    ZBTCA_Types::OutputCfg m_cfg{};
public:
    explicit ZBTCA_Output(const ZBTCA_Bin& bin) : m_bin(bin) {};

    ZBTCA_Response operator()(const ZBTCA_Details::fs::path& path) const {
        static const std::string TAB(2, ' '); // MSVC can not use constexpr!

        using namespace ZBTCA_Types;
        using namespace ZBTCA_Details;

        ZBTCA_OUTPUT_ASSERT(!m_bin.Empty(), "BinaryObject is empty");

        const std::string toolname =
            m_cfg.Annotation.ToolName.empty()
                ? "ZBinary2CArray"
                : m_cfg.Annotation.ToolName;
        const std::string username =
            m_cfg.Annotation.Runner
            ? m_cfg.Annotation.RunnerName.empty()
                    ? GetSysUserName()
                    : m_cfg.Annotation.RunnerName
            : "Unknown User";
        const std::string timestring = m_cfg.Annotation.Time ? []() {
            const std::time_t time_t = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
            std::tm* tm = std::localtime(&time_t);
            return fmt::format(
            "{:04}/{:02}/{:02} {:02}:{:02}",
            tm->tm_year + 1900,
            tm->tm_mon + 1,
                tm->tm_mday,
                tm->tm_hour,
                tm->tm_min
            );
        }() : "";
        const std::string filename = ReplaceInvalidChar(m_bin.GetFilename());
        const std::string inc_guard_start = m_cfg.IncGuard ? fmt::format(
            "#ifndef ZBTCA_{}_HEADER\n#define ZBTCA_{}_HEADER\n\n",
            filename, filename
        ) : "";
        const std::string inc_guard_end = m_cfg.IncGuard ? fmt::format(
            "\n\n#endif // ZBTCA_{}_HEADER",
            filename
        ) : "\n";
        const std::string head_string = [&]() {
            std::ostringstream oss;
            oss << fmt::format("// Binary C-Array {} File", m_cfg.HeaderOnly ? "Header" : "Source");
            if (m_cfg.Annotation.Tool) {
                oss << fmt::format(" | Generation Tool - {}.\n", toolname);
            } else {
                oss << ".\n";
            }

            if (m_cfg.Annotation.Runner || m_cfg.Annotation.Time) {
                oss << fmt::format(
                  "// {}{}{}.\n",
                  m_cfg.Annotation.Runner ? fmt::format("Generate by {}", username) : "",
                  m_cfg.Annotation.Runner && m_cfg.Annotation.Time ? ", " : "",
                  m_cfg.Annotation.Time ? fmt::format("{} {}", m_cfg.Annotation.Runner ? "at" : "At", timestring) : ""
                );
            }

            if (m_cfg.Annotation.File) {
                oss << fmt::format(
                  "// Original file: \"{}\".\n// Output file: \"{}\".\n",
                  ZBTCA_Details::fs::absolute(m_bin.GetPath()).string(),
                  ZBTCA_Details::fs::absolute(path).string()
                );
            }

            oss << std::endl;

            return oss.str();
        }();
        const char* storage_specifier = [&]() {
            if (m_cfg.HeaderOnly) {
                switch (m_cfg.StorageSpecifier) {
                    case OutputCfg::StorageSpecifier_none: return "";
                    case OutputCfg::StorageSpecifier_inline: return "inline ";
                    case OutputCfg::StorageSpecifier_static: return "static ";
                    default: return "";
                }
            }

            return "";
        }();
        const char* const_specifier = [&]() {
            switch (m_cfg.ConstSpecifier) {
                case OutputCfg::ConstSpecifier_none: return "";
                case OutputCfg::ConstSpecifier_const: return "const ";
                case OutputCfg::ConstSpecifier_constexpr:
                    {
                        if (m_cfg.HeaderOnly) {
                            return "constexpr ";
                        } else {
                            return ""; // `constexpr` cannot be shared through an extern declaration
                        }
                    }
                default: return "";
            }
        }();
        const char* const_external_linkage = [&]() {
            if (!m_cfg.HeaderOnly && m_cfg.ConstSpecifier == OutputCfg::ConstSpecifier_const) {
                return "extern ";
            }
            return "";
        }();

        if (fs::path parentDir = path.parent_path(); !parentDir.empty() && !fs::exists(parentDir)) {
            ZBTCA_OUTPUT_ASSERT(fs::create_directories(parentDir), "Can't create directory: " + parentDir.string());
        }
        std::ofstream ofs(path);
        ZBTCA_OUTPUT_ASSERT(ofs.is_open(), "Can't open file for writing: " + path.string());

        ofs << head_string << (m_cfg.HeaderOnly ? inc_guard_start : "");

        ofs << fmt::format("{}{}{}unsigned int {}_len = {};\n", const_external_linkage, storage_specifier, const_specifier,filename, m_bin.GetSize());
        if (m_cfg.HeaderOnly) {
            ofs << fmt::format("#define {}_size {}_len\n", filename, filename);
        }
        ofs << fmt::format("{}{}{}{} {}[] = {{\n", const_external_linkage, storage_specifier, const_specifier, m_cfg.ExportTypeFlags.GetName(), filename);

        std::size_t num_bytes = m_bin.GetSize();
        std::size_t elements = 0;
        const std::uint8_t* bin_ptr = m_bin.GetData().data();

        ZBTCA_OUTPUT_ASSERT(bin_ptr != nullptr || num_bytes != 0, "Invalid or empty data in BinaryObject");
        if (bin_ptr == nullptr || num_bytes == 0) {
            ofs.close();
            if (fs::exists(path)) fs::remove(path);
        }

        try {
            if (!m_cfg.MoreTidy) ofs << TAB;
            switch (m_cfg.ExportTypeFlags.GetType()) {
                #define General_Parameters(num) ofs, bin_ptr, num_bytes, m_cfg.MoreTidy
                case TypeFlags_u8:
                elements = WriteOutBin<std::uint8_t>(General_Parameters(4), m_cfg.NumsPerLine ? m_cfg.NumsPerLine : 12);
                break;
                case TypeFlags_u16:
                elements = WriteOutBin<std::uint16_t>(General_Parameters(4), m_cfg.NumsPerLine ? m_cfg.NumsPerLine : 8);
                break;
                case TypeFlags_u32:
                elements = WriteOutBin<std::uint32_t>(General_Parameters(4), m_cfg.NumsPerLine ? m_cfg.NumsPerLine : 6);
                break;
                case TypeFlags_u64:
                elements = WriteOutBin<std::uint64_t>(General_Parameters(4), m_cfg.NumsPerLine ? m_cfg.NumsPerLine : 4);
                break;
                default:
                    {
                        ofs.close();
                        ZBTCA_OUTPUT_ASSERT(false, "Invalid flag type encountered");
                    }
                #undef General_Parameters
            }
        } catch (const std::exception& e) {
            ofs.close();
            ZBTCA_OUTPUT_ASSERT(false, std::string("Exception while writing data: ") + e.what());
        }

        ofs << "\n}; // " << filename << "_end [" << elements << " elements]" << (m_cfg.HeaderOnly ? inc_guard_end : "");

        ofs.close();

        if (!m_cfg.HeaderOnly) {
            fs::path header_path = path.parent_path() / fs::path(path.stem().string() + ".h");

            const std::string header_head_string = [&]() {
                std::ostringstream oss;
                oss << "// Binary C-Array Extern Header File";
                if (m_cfg.Annotation.Tool) {
                    oss << fmt::format(" | Generation Tool - {}.\n", toolname);
                } else {
                    oss << ".\n";
                }

                if (m_cfg.Annotation.Runner || m_cfg.Annotation.Time) {
                    oss << fmt::format(
                      "// {}{}{}.\n",
                      m_cfg.Annotation.Runner ? fmt::format("Generate by {}", username) : "",
                      m_cfg.Annotation.Runner && m_cfg.Annotation.Time ? ", " : "",
                      m_cfg.Annotation.Time ? fmt::format("{} {}", m_cfg.Annotation.Runner ? "at" : "At", timestring) : ""
                    );
                }

                if (m_cfg.Annotation.File) {
                    oss << fmt::format(
                      "// Original file: \"{}\".\n// Output file: \"{}\".\n",
                      ZBTCA_Details::fs::absolute(m_bin.GetPath()).string(),
                      ZBTCA_Details::fs::absolute(header_path).string()
                    );
                }

                oss << std::endl;

                return oss.str();
            }();

            std::ofstream h_ofs(header_path);
            ZBTCA_OUTPUT_ASSERT(h_ofs.is_open(), "Can't open file for writing: " + header_path.string());

            h_ofs << header_head_string << inc_guard_start;

            h_ofs << "#ifdef __cplusplus\nextern \"C\" {\n";

            h_ofs << fmt::format("extern {}{}unsigned int {}_len;\n", storage_specifier, const_specifier, filename);
            h_ofs << fmt::format("#define {}_size {}_len\n", filename, filename);
            h_ofs << fmt::format("extern {}{}{} {}[];\n", storage_specifier, const_specifier, m_cfg.ExportTypeFlags.GetName(), filename);

            h_ofs << "}\n#endif";

            h_ofs << inc_guard_end;
        }

        return ZBTCA_Response();
    }

    [[nodiscard]] const ZBTCA_Bin& BinObject() const { return m_bin; }
    [[nodiscard]] ZBTCA_Types::OutputCfg& Config() { return m_cfg; }
};


#endif //ZBINARY2CARRAY_OUTPUT_HPP
