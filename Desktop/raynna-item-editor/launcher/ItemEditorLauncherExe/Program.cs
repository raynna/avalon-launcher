using System.Globalization;

namespace ItemEditorLauncher;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        CultureInfo.DefaultThreadCurrentCulture = CultureInfo.InvariantCulture;
        CultureInfo.DefaultThreadCurrentUICulture = CultureInfo.InvariantCulture;

        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
    }
}
