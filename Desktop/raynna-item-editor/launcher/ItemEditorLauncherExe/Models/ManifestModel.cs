namespace ItemEditorLauncher.Models;

internal sealed class ManifestModel
{
    public string Version { get; set; } = string.Empty;
    public string ZipUrl { get; set; } = string.Empty;
    public string? ZipSha256 { get; set; }
    public string? Notes { get; set; }
}
