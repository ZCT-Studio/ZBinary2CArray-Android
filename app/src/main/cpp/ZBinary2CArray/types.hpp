// Licensed under the MIT License

//
// Created by wanjiangzhi on 2026/9/16.
//

#ifndef ZBINARY2CARRAY_TYPES_HPP
#define ZBINARY2CARRAY_TYPES_HPP

#include <cstdint>
#include <string>
#include <vector>

namespace ZBTCA_Types {
    enum ETypeFlags : uint8_t {
        TypeFlags_u8,
        TypeFlags_u16,
        TypeFlags_u32,
        TypeFlags_u64,
    };

    struct TypeFlag {
        TypeFlag(const char* tName, const ETypeFlags t)
            : m_name(tName),
              m_flag(t) {}

        [[nodiscard]] const char* GetName() const { return m_name; }
        [[nodiscard]] ETypeFlags GetType() const { return m_flag; }
        // ReSharper disable once CppNonExplicitConversionOperator
        operator const char*() const { return m_name; }
        // ReSharper disable once CppNonExplicitConversionOperator
        operator ETypeFlags() const { return m_flag; }

    private:
        const char* m_name;
        ETypeFlags m_flag;
    };

    namespace TypeFlags {
        inline TypeFlag u8("unsigned char", TypeFlags_u8);
        inline TypeFlag u16("unsigned short", TypeFlags_u16);
        inline TypeFlag u32("unsigned int", TypeFlags_u32);
        inline TypeFlag u64("unsigned long long", TypeFlags_u64);
    }

    struct OutputCfg {
        enum EStorageSpecifier : uint8_t {
            StorageSpecifier_none,
            StorageSpecifier_static,
            StorageSpecifier_inline
        };
        enum EConstSpecifier : uint8_t {
            ConstSpecifier_none,
            ConstSpecifier_const,
            ConstSpecifier_constexpr
        };
        /*enum ENumFormat : uint8_t {
            NumFormat_bin,
            NumFormat_oct,
            NumFormat_dec,
            NumFormat_hex
        };*/
        struct AnnotationCfg {
            bool Tool = true;
            bool Runner = true;
            bool File = true;
            bool Size = true;
            bool Time = true;
            std::string ToolName = {};
            std::string RunnerName = {};
        };

        TypeFlag ExportTypeFlags = TypeFlags::u8;
        bool HeaderOnly = true;
        bool IncGuard = true;
        EStorageSpecifier StorageSpecifier = StorageSpecifier_none;
        EConstSpecifier ConstSpecifier = ConstSpecifier_none;
        /*ENumFormat NumFormat = NumFormat_hex;*/
        bool MoreTidy = true;
        int NumsPerLine = 0;
        AnnotationCfg Annotation = {};
    };

    using dynamic_bin_t = std::vector<uint8_t>;
}

#endif //ZBINARY2CARRAY_TYPES_HPP
