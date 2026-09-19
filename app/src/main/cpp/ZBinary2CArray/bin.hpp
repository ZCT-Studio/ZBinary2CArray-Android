// Licensed under the MIT License

//
// Created by wanjiangzhi on 2026/9/16.
//

#ifndef ZBINARY2CARRAY_BIN_H
#define ZBINARY2CARRAY_BIN_H

#include "types.hpp"
#include "details.hpp"
#include <fstream>

class ZBTCA_Bin {
protected:
    ZBTCA_Types::dynamic_bin_t m_bin_data;
    ZBTCA_Details::fs::path m_path;
    std::size_t m_size{};
public:
    explicit ZBTCA_Bin(ZBTCA_Details::fs::path path) : m_path(std::move(path)) {
        Reload();
    };
    ~ZBTCA_Bin() = default;
    void Reload() {
        if (!ZBTCA_Details::fs::exists(m_path))
            throw std::invalid_argument("File not found: " + m_path.string());
        if (!ZBTCA_Details::fs::is_regular_file(m_path))
            throw std::invalid_argument("Path is not a regular file: " + m_path.string());
        std::ifstream ifs(m_path, std::ios::binary);
        if (!ifs) throw std::runtime_error("Failed to open file: " + m_path.string());
        m_size = ZBTCA_Details::fs::file_size(m_path);
        m_bin_data = ZBTCA_Types::dynamic_bin_t(m_size);
        ifs.read(reinterpret_cast<char*>(m_bin_data.data()), static_cast<std::streamsize>(m_size));
        if (!ifs) throw std::runtime_error("Failed to read file or incomplete read: " + m_path.string());
    }
    void Reload(const std::string& new_path) {
        m_path = new_path;
        Reload();
    }
    void Clear() {
        m_path.clear();
        m_bin_data.clear();
        m_size = 0;
    }

    [[nodiscard]] const ZBTCA_Types::dynamic_bin_t& GetData() const { return m_bin_data; }
    [[nodiscard]] ZBTCA_Details::fs::path GetPath() const { return m_path; }
    [[nodiscard]] std::string GetFilename() const { return m_path.filename().string(); }
    [[nodiscard]] size_t GetSize() const { return m_size; }
    [[nodiscard]] bool Empty() const { return m_bin_data.empty(); }
};

#endif // ZBINARY2CARRAY_BIN_H
