#include <node_api.h>
#include <windows.h>
#include <string>
#include <vector>

namespace airlink {
void InjectMouseMove(double normX, double normY);
void InjectMouseClick(const std::string& button, const std::string& action);
void InjectMouseScroll(double deltaX, double deltaY);
void InjectKeyPress(WORD vkCode, const std::vector<std::string>& modifiers, const std::string& action);
void InjectTextInput(const std::wstring& utf16Text);
}

static std::string GetUtf8String(napi_env env, napi_value val) {
    size_t len = 0;
    napi_get_value_string_utf8(env, val, nullptr, 0, &len);
    std::string out(len, '\0');
    napi_get_value_string_utf8(env, val, &out[0], len + 1, &len);
    return out;
}

static std::wstring GetUtf16String(napi_env env, napi_value val) {
    size_t len = 0;
    napi_get_value_string_utf16(env, val, nullptr, 0, &len);
    std::wstring out(len, L'\0');
    napi_get_value_string_utf16(env, val, reinterpret_cast<char16_t*>(&out[0]), len + 1, &len);
    return out;
}

static napi_value NapiMouseMove(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value args[2];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    double x = 0.5, y = 0.5;
    if (argc >= 2) {
        napi_get_value_double(env, args[0], &x);
        napi_get_value_double(env, args[1], &y);
    }
    airlink::InjectMouseMove(x, y);
    return nullptr;
}

static napi_value NapiMouseClick(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value args[2];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    std::string button = argc >= 1 ? GetUtf8String(env, args[0]) : "left";
    std::string action = argc >= 2 ? GetUtf8String(env, args[1]) : "click";
    airlink::InjectMouseClick(button, action);
    return nullptr;
}

static napi_value NapiMouseScroll(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value args[2];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    double dx = 0.0, dy = 0.0;
    if (argc >= 2) {
        napi_get_value_double(env, args[0], &dx);
        napi_get_value_double(env, args[1], &dy);
    }
    airlink::InjectMouseScroll(dx, dy);
    return nullptr;
}

static napi_value NapiKeyPress(napi_env env, napi_callback_info info) {
    size_t argc = 3;
    napi_value args[3];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    int32_t vk = 0;
    if (argc >= 1) napi_get_value_int32(env, args[0], &vk);

    std::vector<std::string> modifiers;
    if (argc >= 2) {
        bool isArr = false;
        napi_is_array(env, args[1], &isArr);
        if (isArr) {
            uint32_t count = 0;
            napi_get_array_length(env, args[1], &count);
            for (uint32_t i = 0; i < count; ++i) {
                napi_value elem;
                napi_get_element(env, args[1], i, &elem);
                modifiers.push_back(GetUtf8String(env, elem));
            }
        }
    }
    std::string action = argc >= 3 ? GetUtf8String(env, args[2]) : "press";
    airlink::InjectKeyPress(static_cast<WORD>(vk), modifiers, action);
    return nullptr;
}

static napi_value NapiTextInput(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value args[1];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc >= 1) {
        airlink::InjectTextInput(GetUtf16String(env, args[0]));
    }
    return nullptr;
}

static napi_value Init(napi_env env, napi_value exports) {
    napi_property_descriptor desc[] = {
        {"injectMouseMove", nullptr, NapiMouseMove, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"injectMouseClick", nullptr, NapiMouseClick, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"injectMouseScroll", nullptr, NapiMouseScroll, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"injectKeyPress", nullptr, NapiKeyPress, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"injectTextInput", nullptr, NapiTextInput, nullptr, nullptr, nullptr, napi_default, nullptr}
    };
    napi_define_properties(env, exports, sizeof(desc) / sizeof(desc[0]), desc);
    return exports;
}

NAPI_MODULE(NODE_GYP_MODULE_NAME, Init)
