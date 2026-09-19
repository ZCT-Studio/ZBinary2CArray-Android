// Licensed under the MIT License

//
// Created by wanjiangzhi on 2026/9/17.
//

#ifndef ZBINARY2CARRAY_RESPONSE_HPP
#define ZBINARY2CARRAY_RESPONSE_HPP

#include <utility>
#include <string>

class ZBTCA_Response {
    std::string m_msg;
    bool m_status{};
public:
    explicit ZBTCA_Response(std::string msg = {}, const bool status = true) : m_msg(std::move(msg)), m_status(status) {}

    [[nodiscard]] bool status() const { return m_status; }
    // ReSharper disable once CppNonExplicitConversionOperator
    operator bool() const { return status(); }
    [[nodiscard]] const std::string& msg() const { return m_msg; }
};


#endif //ZBINARY2CARRAY_RESPONSE_HPP
