namespace ItemEditorLauncher.Models;

internal sealed class LauncherConfig
{
    public string AppName { get; set; } = "Raynna Item Editor";
    public UpdatesConfig Updates { get; set; } = new();
    public JavaConfig Java { get; set; } = new();
}

internal sealed class UpdatesConfig
{
    public string ManifestUrl { get; set; } = string.Empty;
    public int RequestTimeoutSeconds { get; set; } = 60;
}

internal sealed class JavaConfig
{
    public int PreferredMajorVersion { get; set; } = 21;
    public string DownloadUrl { get; set; } = string.Empty;
}
