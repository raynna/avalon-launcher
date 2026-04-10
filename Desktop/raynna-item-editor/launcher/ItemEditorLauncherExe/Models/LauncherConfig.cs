namespace ItemEditorLauncher.Models;

internal sealed class LauncherConfig
{
    public string AppName { get; set; } = "Raynna Item Editor";
    public UpdatesConfig Updates { get; set; } = new();
    public JavaConfig Java { get; set; } = new();
}

internal sealed class UpdatesConfig
{
    public string ManifestUrl { get; set; } = "https://raw.githubusercontent.com/raynna/raynna-item-editor-release/main/manifest.json";
    public int RequestTimeoutSeconds { get; set; } = 60;
}

internal sealed class JavaConfig
{
    public int PreferredMajorVersion { get; set; } = 21;
    public string DownloadUrl { get; set; } = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse";
}
