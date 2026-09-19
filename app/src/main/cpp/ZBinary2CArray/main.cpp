// Licensed under the MIT License

//
// Created by wanjiangzhi on 2026/9/18.
//

#include "ZBinary2CArray/zbtca.h"

#include <exception>
#include <filesystem>
#include <iostream>
#include <string>

namespace fs = ZBTCA_Details::fs;

namespace {
    struct CliArgs {
        std::string input;
        std::string output;
        std::string type = "u8";
        bool header_only = true;
        bool inc_guard = true;
        std::string storage = "none";
        std::string const_spec = "none";
        int nums_per_line = 0;
        bool more_tidy = true;
        bool anno_tool = true;
        bool anno_runner = true;
        bool anno_file = true;
        bool anno_size = true;
        bool anno_time = true;
        std::string tool_name;
        std::string runner_name;
        bool help = false;
    };

    void PrintUsage(std::ostream& os) {
        os <<
            "Usage: zbtca-cli <input> [options]\n"
            "\n"
            "Positional:\n"
            "  <input>                       Input binary file path\n"
            "\n"
            "Options:\n"
            "  -o, --output <path>           Output path (default: derived from input)\n"
            "  -t, --type <type>             Element type: u8|u16|u32|u64 (default: u8)\n"
            "      --header-only             Header-only output (default)\n"
            "      --source                  Source + extern header output\n"
            "      --no-inc-guard            Disable include guard\n"
            "      --storage <spec>          Storage: none|static|inline (default: none)\n"
            "      --const <spec>            Const: none|const|constexpr (default: none)\n"
            "  -n, --nums-per-line <n>       Elements per line (default: 0 = auto by type)\n"
            "      --no-tidy                 Disable tidy formatting\n"
            "      --no-anno-tool            Disable tool annotation\n"
            "      --no-anno-runner          Disable runner annotation\n"
            "      --no-anno-file            Disable file annotation\n"
            "      --no-anno-size            Disable size annotation\n"
            "      --no-anno-time            Disable time annotation\n"
            "      --tool-name <name>        Custom tool name\n"
            "      --runner-name <name>      Custom runner name\n"
            "  -h, --help                    Show this help\n";
    }

    fs::path DeriveOutput(const fs::path& input, const bool header_only) {
        fs::path out = input;
        out.replace_extension(header_only ? ".hpp" : ".cpp");
        return out;
    }

    bool ParseArgs(const int argc, char* argv[], CliArgs& out) {
        auto need_value = [&](int& i, const std::string& flag, std::string& dest) -> bool {
            if (i + 1 >= argc) {
                std::cerr << "Error: " << flag << " requires a value\n";
                return false;
            }
            dest = argv[++i];
            return true;
        };
        auto need_value_int = [&](int& i, const std::string& flag, int& dest) -> bool {
            if (i + 1 >= argc) {
                std::cerr << "Error: " << flag << " requires a value\n";
                return false;
            }
            const std::string v = argv[++i];
            try {
                dest = std::stoi(v);
            } catch (...) {
                std::cerr << "Error: invalid integer for " << flag << ": " << v << "\n";
                return false;
            }
            if (dest < 0) {
                std::cerr << "Error: " << flag << " must be non-negative\n";
                return false;
            }
            return true;
        };

        for (int i = 1; i < argc; ++i) {
            const std::string arg = argv[i];
            if (arg == "-h" || arg == "--help") { out.help = true; return true; }
            else if (arg == "-o" || arg == "--output") { if (!need_value(i, arg, out.output)) return false; }
            else if (arg == "-t" || arg == "--type") { if (!need_value(i, arg, out.type)) return false; }
            else if (arg == "--header-only") { out.header_only = true; }
            else if (arg == "--source") { out.header_only = false; }
            else if (arg == "--no-inc-guard") { out.inc_guard = false; }
            else if (arg == "--storage") { if (!need_value(i, arg, out.storage)) return false; }
            else if (arg == "--const") { if (!need_value(i, arg, out.const_spec)) return false; }
            else if (arg == "-n" || arg == "--nums-per-line") { if (!need_value_int(i, arg, out.nums_per_line)) return false; }
            else if (arg == "--no-tidy") { out.more_tidy = false; }
            else if (arg == "--no-anno-tool") { out.anno_tool = false; }
            else if (arg == "--no-anno-runner") { out.anno_runner = false; }
            else if (arg == "--no-anno-file") { out.anno_file = false; }
            else if (arg == "--no-anno-size") { out.anno_size = false; }
            else if (arg == "--no-anno-time") { out.anno_time = false; }
            else if (arg == "--tool-name") { if (!need_value(i, arg, out.tool_name)) return false; }
            else if (arg == "--runner-name") { if (!need_value(i, arg, out.runner_name)) return false; }
            else if (arg.starts_with('-')) {
                std::cerr << "Error: unknown option '" << arg << "'\n";
                return false;
            } else if (out.input.empty()) {
                out.input = arg;
            } else {
                std::cerr << "Error: unexpected extra argument '" << arg << "'\n";
                return false;
            }
        }
        return true;
    }

    int ApplyConfig(const CliArgs& args, ZBTCA_Types::OutputCfg& cfg) {
        using namespace ZBTCA_Types;

        if (args.type == "u8") cfg.ExportTypeFlags = TypeFlags::u8;
        else if (args.type == "u16") cfg.ExportTypeFlags = TypeFlags::u16;
        else if (args.type == "u32") cfg.ExportTypeFlags = TypeFlags::u32;
        else if (args.type == "u64") cfg.ExportTypeFlags = TypeFlags::u64;
        else {
            std::cerr << "Error: invalid type '" << args.type << "' (expected u8|u16|u32|u64)\n";
            return 2;
        }

        cfg.HeaderOnly = args.header_only;
        cfg.IncGuard = args.inc_guard;

        if (args.storage == "none") cfg.StorageSpecifier = OutputCfg::StorageSpecifier_none;
        else if (args.storage == "static") cfg.StorageSpecifier = OutputCfg::StorageSpecifier_static;
        else if (args.storage == "inline") cfg.StorageSpecifier = OutputCfg::StorageSpecifier_inline;
        else {
            std::cerr << "Error: invalid storage '" << args.storage << "' (expected none|static|inline)\n";
            return 2;
        }

        if (args.const_spec == "none") cfg.ConstSpecifier = OutputCfg::ConstSpecifier_none;
        else if (args.const_spec == "const") cfg.ConstSpecifier = OutputCfg::ConstSpecifier_const;
        else if (args.const_spec == "constexpr") cfg.ConstSpecifier = OutputCfg::ConstSpecifier_constexpr;
        else {
            std::cerr << "Error: invalid const '" << args.const_spec << "' (expected none|const|constexpr)\n";
            return 2;
        }

        cfg.NumsPerLine = args.nums_per_line;
        cfg.MoreTidy = args.more_tidy;
        cfg.Annotation.Tool = args.anno_tool;
        cfg.Annotation.Runner = args.anno_runner;
        cfg.Annotation.File = args.anno_file;
        cfg.Annotation.Size = args.anno_size;
        cfg.Annotation.Time = args.anno_time;
        if (!args.tool_name.empty()) cfg.Annotation.ToolName = args.tool_name;
        if (!args.runner_name.empty()) cfg.Annotation.RunnerName = args.runner_name;

        return 0;
    }
}

int main(const int argc, char* argv[]) {
    CliArgs args;
    if (!ParseArgs(argc, argv, args)) { PrintUsage(std::cerr); return 2; }
    if (args.help) { PrintUsage(std::cout); return 0; }
    if (args.input.empty()) {
        std::cerr << "Error: missing input file\n";
        PrintUsage(std::cerr);
        return 2;
    }

    const fs::path output_path = args.output.empty()
        ? DeriveOutput(args.input, args.header_only)
        : fs::path(args.output);

    try {
        ZBTCA_Bin bin(args.input);
        ZBTCA_Output output(bin);
        if (const int ec = ApplyConfig(args, output.Config())) return ec;
        if (const ZBTCA_Response resp = output(output_path); !resp.status()) {
            std::cerr << "Error: " << resp.msg() << "\n";
            return 1;
        }
    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << "\n";
        return 1;
    }
    return 0;
}
