using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

namespace CraftLive.InputHelper;

internal static class Program
{
    private const uint InputKeyboard = 1;
    private const uint KeyUp = 0x0002;
    private const ushort VkControl = 0x11;
    private const ushort VkEnter = 0x0D;
    private const ushort VkT = 0x54;
    private const ushort VkV = 0x56;
    private const int SwRestore = 9;

    private delegate bool EnumWindowsCallback(nint window, nint state);

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInput
    {
        public ushort VirtualKey;
        public ushort ScanCode;
        public uint Flags;
        public uint Time;
        public nuint ExtraInfo;
    }

    // This helper is built only for win-x64. INPUT is 40 bytes on 64-bit Windows.
    [StructLayout(LayoutKind.Explicit, Size = 40)]
    private struct Input
    {
        [FieldOffset(0)] public uint Type;
        [FieldOffset(8)] public KeyboardInput Keyboard;
    }

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern bool EnumWindows(EnumWindowsCallback callback, nint state);

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern bool IsWindowVisible(nint window);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, ExactSpelling = true)]
    private static extern int GetWindowTextW(nint window, StringBuilder title, int maximumLength);

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern int GetWindowTextLengthW(nint window);

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern bool ShowWindowAsync(nint window, int command);

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern bool SetForegroundWindow(nint window);

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern nint SetFocus(nint window);

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern nint GetForegroundWindow();

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern uint GetWindowThreadProcessId(nint window, nint processId);

    [DllImport("kernel32.dll", ExactSpelling = true)]
    private static extern uint GetCurrentThreadId();

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern bool AttachThreadInput(uint sourceThread, uint targetThread, bool attach);

    [DllImport("user32.dll", SetLastError = true, ExactSpelling = true)]
    private static extern uint SendInput(uint count, Input[] inputs, int inputSize);

    public static int Main(string[] arguments)
    {
        if (!OperatingSystem.IsWindows()) return 10;
        if (arguments.Length != 1) return 11;

        var minecraftWindow = FindMinecraftWindow();
        if (minecraftWindow == nint.Zero) return 2;
        if (string.Equals(arguments[0], "detect", StringComparison.Ordinal)) return 0;
        if (!string.Equals(arguments[0], "send", StringComparison.Ordinal)) return 11;

        return SendMinecraftCommand(minecraftWindow) ? 0 : 3;
    }

    private static nint FindMinecraftWindow()
    {
        nint result = nint.Zero;
        EnumWindows((window, _) =>
        {
            if (!IsWindowVisible(window)) return true;
            var length = GetWindowTextLengthW(window);
            if (length <= 0 || length > 1024) return true;

            var title = new StringBuilder(length + 1);
            if (GetWindowTextW(window, title, title.Capacity) <= 0) return true;
            if (!title.ToString().Contains("Minecraft", StringComparison.OrdinalIgnoreCase)) return true;

            result = window;
            return false;
        }, nint.Zero);
        return result;
    }

    private static bool SendMinecraftCommand(nint window)
    {
        ShowWindowAsync(window, SwRestore);
        if (!FocusWindow(window)) return false;
        Thread.Sleep(90);

        if (!SendKeys(Key(VkT), Key(VkT, true))) return false;
        Thread.Sleep(65);
        if (!SendKeys(Key(VkControl), Key(VkV), Key(VkV, true), Key(VkControl, true))) return false;
        Thread.Sleep(45);
        return SendKeys(Key(VkEnter), Key(VkEnter, true));
    }

    private static bool FocusWindow(nint window)
    {
        var currentThread = GetCurrentThreadId();
        var foregroundThread = GetWindowThreadProcessId(GetForegroundWindow(), nint.Zero);
        var attached = foregroundThread != 0 && foregroundThread != currentThread &&
                       AttachThreadInput(currentThread, foregroundThread, true);
        try
        {
            var foregroundSet = SetForegroundWindow(window);
            SetFocus(window);
            return foregroundSet;
        }
        finally
        {
            if (attached) AttachThreadInput(currentThread, foregroundThread, false);
        }
    }

    private static Input Key(ushort virtualKey, bool released = false) => new()
    {
        Type = InputKeyboard,
        Keyboard = new KeyboardInput
        {
            VirtualKey = virtualKey,
            Flags = released ? KeyUp : 0
        }
    };

    private static bool SendKeys(params Input[] inputs) =>
        SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<Input>()) == (uint)inputs.Length;
}
