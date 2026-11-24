#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <string>
#include <vector>

namespace airlink {

void EnsurePerMonitorDpiAwareness() {
    static bool initialized = false;
    if (initialized) return;
    initialized = true;
    SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
}

static bool IsExtendedVirtualKey(WORD vk) {
    switch (vk) {
        case VK_INSERT:
        case VK_DELETE:
        case VK_HOME:
        case VK_END:
        case VK_PRIOR:
        case VK_NEXT:
        case VK_LEFT:
        case VK_UP:
        case VK_RIGHT:
        case VK_DOWN:
        case VK_LWIN:
        case VK_RWIN:
        case VK_DIVIDE:
        case VK_NUMLOCK:
            return true;
        default:
            return false;
    }
}

void InjectMouseMove(double normX, double normY) {
    EnsurePerMonitorDpiAwareness();

    const double clampedX = normX < 0.0 ? 0.0 : (normX > 1.0 ? 1.0 : normX);
    const double clampedY = normY < 0.0 ? 0.0 : (normY > 1.0 ? 1.0 : normY);

    const LONG absX = static_cast<LONG>(clampedX * 65535.0);
    const LONG absY = static_cast<LONG>(clampedY * 65535.0);

    INPUT input = {};
    input.type = INPUT_MOUSE;
    input.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK;
    input.mi.dx = absX;
    input.mi.dy = absY;
    SendInput(1, &input, sizeof(INPUT));
}

void InjectMouseClick(const std::string& button, const std::string& action) {
    EnsurePerMonitorDpiAwareness();

    DWORD downFlag = MOUSEEVENTF_LEFTDOWN;
    DWORD upFlag = MOUSEEVENTF_LEFTUP;

    if (button == "right") {
        downFlag = MOUSEEVENTF_RIGHTDOWN;
        upFlag = MOUSEEVENTF_RIGHTUP;
    } else if (button == "middle") {
        downFlag = MOUSEEVENTF_MIDDLEDOWN;
        upFlag = MOUSEEVENTF_MIDDLEUP;
    }

    if (action == "down") {
        INPUT input = {};
        input.type = INPUT_MOUSE;
        input.mi.dwFlags = downFlag;
        SendInput(1, &input, sizeof(INPUT));
    } else if (action == "up") {
        INPUT input = {};
        input.type = INPUT_MOUSE;
        input.mi.dwFlags = upFlag;
        SendInput(1, &input, sizeof(INPUT));
    } else {
        INPUT inputs[2] = {};
        inputs[0].type = INPUT_MOUSE;
        inputs[0].mi.dwFlags = downFlag;
        inputs[1].type = INPUT_MOUSE;
        inputs[1].mi.dwFlags = upFlag;
        SendInput(2, inputs, sizeof(INPUT));
    }
}

void InjectMouseScroll(double deltaX, double deltaY) {
    EnsurePerMonitorDpiAwareness();

    if (deltaY != 0.0) {
        INPUT vWheel = {};
        vWheel.type = INPUT_MOUSE;
        vWheel.mi.dwFlags = MOUSEEVENTF_WHEEL;
        vWheel.mi.mouseData = static_cast<DWORD>(static_cast<LONG>(deltaY * WHEEL_DELTA));
        SendInput(1, &vWheel, sizeof(INPUT));
    }
    if (deltaX != 0.0) {
        INPUT hWheel = {};
        hWheel.type = INPUT_MOUSE;
        hWheel.mi.dwFlags = MOUSEEVENTF_HWHEEL;
        hWheel.mi.mouseData = static_cast<DWORD>(static_cast<LONG>(deltaX * WHEEL_DELTA));
        SendInput(1, &hWheel, sizeof(INPUT));
    }
}

static WORD ModifierToVk(const std::string& mod) {
    if (mod == "ctrl") return VK_CONTROL;
    if (mod == "alt") return VK_MENU;
    if (mod == "shift") return VK_SHIFT;
    if (mod == "win") return VK_LWIN;
    return 0;
}

void InjectKeyPress(WORD vkCode, const std::vector<std::string>& modifiers, const std::string& action) {
    EnsurePerMonitorDpiAwareness();
    std::vector<INPUT> inputs;

    auto pushKey = [&](WORD vk, bool keyUp) {
        if (vk == 0) return;
        INPUT in = {};
        in.type = INPUT_KEYBOARD;
        in.ki.wVk = vk;
        in.ki.dwFlags = (keyUp ? KEYEVENTF_KEYUP : 0) | (IsExtendedVirtualKey(vk) ? KEYEVENTF_EXTENDEDKEY : 0);
        inputs.push_back(in);
    };

    if (action == "down") {
        for (const auto& m : modifiers) pushKey(ModifierToVk(m), false);
        pushKey(vkCode, false);
    } else if (action == "up") {
        pushKey(vkCode, true);
        for (auto it = modifiers.rbegin(); it != modifiers.rend(); ++it) {
            pushKey(ModifierToVk(*it), true);
        }
    } else {
        for (const auto& m : modifiers) pushKey(ModifierToVk(m), false);
        pushKey(vkCode, false);
        pushKey(vkCode, true);
        for (auto it = modifiers.rbegin(); it != modifiers.rend(); ++it) {
            pushKey(ModifierToVk(*it), true);
        }
    }

    if (!inputs.empty()) {
        SendInput(static_cast<UINT>(inputs.size()), inputs.data(), sizeof(INPUT));
    }
}

void InjectTextInput(const std::wstring& utf16Text) {
    EnsurePerMonitorDpiAwareness();
    if (utf16Text.empty()) return;

    std::vector<INPUT> inputs;
    inputs.reserve(utf16Text.size() * 2);

    for (wchar_t ch : utf16Text) {
        if (ch == L'\r') continue;
        if (ch == L'\n') {
            INPUT down = {};
            down.type = INPUT_KEYBOARD;
            down.ki.wVk = VK_RETURN;
            INPUT up = down;
            up.ki.dwFlags = KEYEVENTF_KEYUP;
            inputs.push_back(down);
            inputs.push_back(up);
            continue;
        }
        INPUT down = {};
        down.type = INPUT_KEYBOARD;
        down.ki.wScan = static_cast<WORD>(ch);
        down.ki.dwFlags = KEYEVENTF_UNICODE;

        INPUT up = down;
        up.ki.dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP;

        inputs.push_back(down);
        inputs.push_back(up);
    }

    if (!inputs.empty()) {
        SendInput(static_cast<UINT>(inputs.size()), inputs.data(), sizeof(INPUT));
    }
}

} // namespace airlink
