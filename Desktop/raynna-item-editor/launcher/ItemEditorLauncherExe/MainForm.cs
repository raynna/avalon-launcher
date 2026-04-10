using System.Diagnostics;
using System.IO.Compression;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using ItemEditorLauncher.Models;

namespace ItemEditorLauncher;

internal sealed class MainForm : Form
{
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true, PropertyNamingPolicy = JsonNamingPolicy.CamelCase, WriteIndented = true };

    private readonly HttpClient _httpClient = new();
    private readonly Label _versionLabel;
    private readonly Label _statusLabel;
    private readonly ProgressBar _progressBar;
    private readonly Button _launchButton;

    private bool _busy;
    private LauncherConfig? _config;
    private StateModel _state = new();

    private string BaseDir => AppContext.BaseDirectory;
    private string LauncherDataRoot => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "RaynnaItemEditorLauncher");
    private string ConfigPath => Path.Combine(BaseDir, "launcher-config.json");
    private string DataDir => LauncherDataRoot;
    private string RuntimeDir => Path.Combine(DataDir, "runtime");
    private string PackageDir => Path.Combine(DataDir, "editor");
    private string StatePath => Path.Combine(DataDir, "state.json");
    private string LaunchScriptPath => Path.Combine(PackageDir, "run-item-editor.bat");

    public MainForm()
    {
        Text = "Raynna Item Editor";
        Size = new Size(420, 300);
        MinimumSize = Size;
        MaximumSize = Size;
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.FromArgb(25, 27, 31);
        ForeColor = Color.FromArgb(232, 234, 237);
        Font = new Font("Segoe UI", 9F, FontStyle.Regular, GraphicsUnit.Point);

        var titleLabel = new Label
        {
            Text = "Item Editor",
            ForeColor = Color.FromArgb(241, 177, 75),
            Font = new Font("Segoe UI", 24F, FontStyle.Bold),
            TextAlign = ContentAlignment.MiddleCenter,
            Bounds = new Rectangle(20, 18, 360, 44)
        };
        Controls.Add(titleLabel);

        _launchButton = new Button
        {
            Text = "Launch",
            BackColor = Color.FromArgb(226, 145, 49),
            ForeColor = Color.FromArgb(28, 18, 8),
            FlatStyle = FlatStyle.Flat,
            Font = new Font("Segoe UI", 20F, FontStyle.Bold),
            Bounds = new Rectangle(20, 78, 360, 72)
        };
        _launchButton.FlatAppearance.BorderSize = 0;
        Controls.Add(_launchButton);

        _versionLabel = new Label
        {
            Text = "Installed version: none",
            ForeColor = Color.FromArgb(191, 201, 215),
            TextAlign = ContentAlignment.MiddleCenter,
            Bounds = new Rectangle(20, 162, 360, 20)
        };
        Controls.Add(_versionLabel);

        _progressBar = new ProgressBar
        {
            Style = ProgressBarStyle.Continuous,
            Bounds = new Rectangle(20, 190, 360, 12),
            Minimum = 0,
            Maximum = 100
        };
        Controls.Add(_progressBar);

        _statusLabel = new Label
        {
            Text = "Ready",
            ForeColor = Color.FromArgb(171, 180, 191),
            TextAlign = ContentAlignment.MiddleCenter,
            Bounds = new Rectangle(20, 210, 360, 20)
        };
        Controls.Add(_statusLabel);

        _launchButton.Click += async (_, _) => await RunUpdateAndLaunchAsync();
        Shown += (_, _) => InitializeLauncher();
    }

    protected override void OnFormClosed(FormClosedEventArgs e)
    {
        _httpClient.Dispose();
        base.OnFormClosed(e);
    }

    private void InitializeLauncher()
    {
        try
        {
            _config = ReadJsonFile<LauncherConfig>(ConfigPath) ?? throw new InvalidOperationException("launcher-config.json is missing or invalid.");
            _state = ReadJsonFile<StateModel>(StatePath) ?? new StateModel();
            Text = _config.AppName;
            _versionLabel.Text = $"Installed version: {DefaultIfEmpty(_state.InstalledVersion, "none")}";
            _statusLabel.Text = "Ready";
        }
        catch (Exception ex)
        {
            SetStatus($"Failed: {ex.Message}");
            SetBusy(true);
            MessageBox.Show(this, ex.Message, "Launcher Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private async Task RunUpdateAndLaunchAsync()
    {
        if (_busy || _config == null) return;
        SetBusy(true);
        try
        {
            EnsureDirectory(DataDir);
            SetStatus("Fetching manifest..."); SetProgress(8);
            var manifest = await GetManifestAsync(_config);
            var javaExe = await EnsureJavaRuntimeAsync(_config);
            await EnsureEditorPackageAsync(_config, manifest);
            _versionLabel.Text = $"Installed version: {DefaultIfEmpty(_state.InstalledVersion, "none")}";
            SetStatus("Starting editor..."); SetProgress(100);
            StartEditor(javaExe);
            Close();
        }
        catch (Exception ex)
        {
            SetProgress(0); SetStatus($"Failed: {ex.Message}");
            MessageBox.Show(this, ex.Message, "Launcher Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        finally
        {
            SetBusy(false);
        }
    }

    private async Task<string> EnsureJavaRuntimeAsync(LauncherConfig config)
    {
        var preferred = Math.Max(8, config.Java.PreferredMajorVersion);
        var bundled = FindBundledJava(RuntimeDir);
        if (!string.IsNullOrWhiteSpace(bundled) && GetJavaMajorVersion(bundled) >= preferred) return bundled;
        var system = FindSystemJava();
        if (!string.IsNullOrWhiteSpace(system) && GetJavaMajorVersion(system) >= preferred) return system;
        if (string.IsNullOrWhiteSpace(config.Java.DownloadUrl)) throw new InvalidOperationException("java.downloadUrl is missing in launcher-config.json");

        SetStatus("Installing Java runtime..."); SetProgress(35);
        var tempZip = Path.Combine(DataDir, "java-runtime.zip");
        TryDeleteFile(tempZip);
        if (Directory.Exists(RuntimeDir)) Directory.Delete(RuntimeDir, true);
        EnsureDirectory(RuntimeDir);
        await DownloadOrCopyAsync(config.Java.DownloadUrl, tempZip);
        ZipFile.ExtractToDirectory(tempZip, RuntimeDir, true);
        TryDeleteFile(tempZip);

        bundled = FindBundledJava(RuntimeDir);
        if (string.IsNullOrWhiteSpace(bundled)) throw new InvalidOperationException("Java installed but java.exe was not found.");
        return bundled;
    }

    private async Task EnsureEditorPackageAsync(LauncherConfig config, ManifestModel manifest)
    {
        if (string.IsNullOrWhiteSpace(manifest.Version) || string.IsNullOrWhiteSpace(manifest.ZipUrl))
            throw new InvalidOperationException("Manifest must include version and zipUrl.");

        var needsDownload = !File.Exists(LaunchScriptPath) || !string.Equals(_state.InstalledVersion, manifest.Version, StringComparison.Ordinal);
        if (needsDownload)
        {
            SetStatus($"Updating editor to {manifest.Version}..."); SetProgress(55);
            var tempZip = Path.Combine(DataDir, "editor-package.zip");
            var tempExtract = Path.Combine(DataDir, "editor-extract");
            var backupDir = Path.Combine(DataDir, "editor-backup");

            TryDeleteFile(tempZip);
            if (Directory.Exists(tempExtract)) Directory.Delete(tempExtract, true);
            if (Directory.Exists(backupDir)) Directory.Delete(backupDir, true);

            await DownloadOrCopyAsync(manifest.ZipUrl, tempZip);

            if (!string.IsNullOrWhiteSpace(manifest.ZipSha256))
            {
                SetStatus("Validating package checksum..."); SetProgress(72);
                var expected = manifest.ZipSha256.Trim().ToLowerInvariant();
                var actual = GetSha256(tempZip);
                if (!string.Equals(actual, expected, StringComparison.Ordinal))
                    throw new InvalidOperationException("Downloaded editor package failed checksum validation.");
            }

            SetStatus("Installing editor..."); SetProgress(82);
            EnsureDirectory(tempExtract);
            ZipFile.ExtractToDirectory(tempZip, tempExtract, true);
            TryDeleteFile(tempZip);

            if (Directory.Exists(PackageDir))
            {
                Directory.Move(PackageDir, backupDir);
            }
            Directory.Move(tempExtract, PackageDir);
            if (Directory.Exists(backupDir))
            {
                Directory.Delete(backupDir, true);
            }
        }

        if (!File.Exists(LaunchScriptPath))
            throw new InvalidOperationException("Installed editor launcher was not found after extraction.");

        _state.InstalledVersion = manifest.Version;
        _state.InstalledAtUtc = DateTime.UtcNow.ToString("O");
        WriteState();
    }

    private void StartEditor(string javaExe)
    {
        var javaHome = Path.GetDirectoryName(Path.GetDirectoryName(javaExe) ?? string.Empty) ?? string.Empty;
        var psi = new ProcessStartInfo
        {
            FileName = "cmd.exe",
            WorkingDirectory = PackageDir,
            UseShellExecute = false
        };
        psi.ArgumentList.Add("/c");
        psi.ArgumentList.Add(LaunchScriptPath);
        if (!string.IsNullOrWhiteSpace(javaHome))
        {
            psi.Environment["JAVA_HOME"] = javaHome;
            psi.Environment["PATH"] = Path.Combine(javaHome, "bin") + ";" + (psi.Environment.ContainsKey("PATH") ? psi.Environment["PATH"] : string.Empty);
        }
        Process.Start(psi);
    }

    private async Task<ManifestModel> GetManifestAsync(LauncherConfig config)
    {
        var manifestUrl = config.Updates.ManifestUrl;
        if (string.IsNullOrWhiteSpace(manifestUrl)) throw new InvalidOperationException("updates.manifestUrl is missing in launcher-config.json");
        if (IsHttpUrl(manifestUrl))
        {
            var timeout = Math.Max(5, config.Updates.RequestTimeoutSeconds);
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(timeout));
            var githubApiManifest = await TryGetManifestViaGitHubApiAsync(manifestUrl, cts.Token);
            if (githubApiManifest != null) return githubApiManifest;

            var url = AppendCacheBust(manifestUrl);
            using var request = new HttpRequestMessage(HttpMethod.Get, url);
            request.Headers.TryAddWithoutValidation("Cache-Control", "no-cache, no-store, max-age=0");
            request.Headers.TryAddWithoutValidation("Pragma", "no-cache");
            request.Headers.TryAddWithoutValidation("Expires", "0");
            using var response = await _httpClient.SendAsync(request, cts.Token);
            response.EnsureSuccessStatusCode();
            var json = await response.Content.ReadAsStringAsync(cts.Token);
            var manifest = JsonSerializer.Deserialize<ManifestModel>(json, JsonOptions);
            return manifest ?? throw new InvalidOperationException("Manifest response was empty.");
        }

        var path = ResolvePath(manifestUrl);
        return ReadJsonFile<ManifestModel>(path) ?? throw new InvalidOperationException("Manifest file is missing or invalid.");
    }

    private async Task<ManifestModel?> TryGetManifestViaGitHubApiAsync(string manifestUrl, CancellationToken cancellationToken)
    {
        if (!TryParseGitHubRawUrl(manifestUrl, out var owner, out var repo, out var gitRef, out var filePath))
            return null;

        try
        {
            var encodedPath = Uri.EscapeDataString(filePath).Replace("%2F", "/");
            var encodedRef = Uri.EscapeDataString(gitRef);
            var apiUrl = $"https://api.github.com/repos/{owner}/{repo}/contents/{encodedPath}?ref={encodedRef}";
            using var request = new HttpRequestMessage(HttpMethod.Get, apiUrl);
            request.Headers.TryAddWithoutValidation("User-Agent", "ItemEditorLauncher/1.0");
            request.Headers.TryAddWithoutValidation("Accept", "application/vnd.github+json");
            request.Headers.TryAddWithoutValidation("Cache-Control", "no-cache, no-store, max-age=0");
            request.Headers.TryAddWithoutValidation("Pragma", "no-cache");
            request.Headers.TryAddWithoutValidation("Expires", "0");
            using var response = await _httpClient.SendAsync(request, cancellationToken);
            if (!response.IsSuccessStatusCode) return null;

            var payload = await response.Content.ReadAsStringAsync(cancellationToken);
            using var doc = JsonDocument.Parse(payload);
            if (!doc.RootElement.TryGetProperty("content", out var contentElement)) return null;

            var content = contentElement.GetString();
            if (string.IsNullOrWhiteSpace(content)) return null;

            var normalized = content.Replace("\n", string.Empty).Replace("\r", string.Empty);
            var json = Encoding.UTF8.GetString(Convert.FromBase64String(normalized));
            return JsonSerializer.Deserialize<ManifestModel>(json, JsonOptions);
        }
        catch
        {
            return null;
        }
    }

    private static bool TryParseGitHubRawUrl(string url, out string owner, out string repo, out string gitRef, out string filePath)
    {
        owner = string.Empty;
        repo = string.Empty;
        gitRef = string.Empty;
        filePath = string.Empty;
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri)) return false;
        if (!string.Equals(uri.Host, "raw.githubusercontent.com", StringComparison.OrdinalIgnoreCase)) return false;

        var parts = uri.AbsolutePath.Trim('/').Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length < 4) return false;

        owner = parts[0];
        repo = parts[1];
        gitRef = parts[2];
        filePath = string.Join("/", parts.Skip(3));
        return !string.IsNullOrWhiteSpace(owner) && !string.IsNullOrWhiteSpace(repo) && !string.IsNullOrWhiteSpace(gitRef) && !string.IsNullOrWhiteSpace(filePath);
    }

    private static string AppendCacheBust(string url)
    {
        var separator = url.Contains('?') ? '&' : '?';
        return $"{url}{separator}cb={DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()}";
    }

    private async Task DownloadOrCopyAsync(string source, string destination)
    {
        EnsureDirectory(Path.GetDirectoryName(destination)!);
        if (IsHttpUrl(source))
        {
            using var response = await _httpClient.GetAsync(source, HttpCompletionOption.ResponseHeadersRead);
            response.EnsureSuccessStatusCode();
            await using var sourceStream = await response.Content.ReadAsStreamAsync();
            await using var fileStream = File.Create(destination);
            await sourceStream.CopyToAsync(fileStream);
            return;
        }

        var sourcePath = ResolvePath(source);
        File.Copy(sourcePath, destination, true);
    }

    private void WriteState()
    {
        EnsureDirectory(DataDir);
        WriteJsonFile(StatePath, _state);
    }

    private static string? FindBundledJava(string runtimeDir) => Directory.Exists(runtimeDir) ? Directory.EnumerateFiles(runtimeDir, "java.exe", SearchOption.AllDirectories).FirstOrDefault() : null;
    private static string? FindSystemJava() => FindOnPath("java.exe");

    private static string? FindOnPath(string exe)
    {
        var paths = (Environment.GetEnvironmentVariable("PATH") ?? string.Empty).Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        foreach (var path in paths)
        {
            try
            {
                var candidate = Path.Combine(path, exe);
                if (File.Exists(candidate)) return candidate;
            }
            catch { }
        }
        return null;
    }

    private static int GetJavaMajorVersion(string javaLaunchExe)
    {
        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = javaLaunchExe,
                UseShellExecute = false,
                RedirectStandardError = true,
                RedirectStandardOutput = true,
                CreateNoWindow = true
            };
            psi.ArgumentList.Add("-version");
            using var process = Process.Start(psi);
            if (process == null) return 0;
            var output = (process.StandardError.ReadToEnd() + "\n" + process.StandardOutput.ReadToEnd()).Trim();
            process.WaitForExit(4000);
            var line = output.Split('\n', StringSplitOptions.RemoveEmptyEntries).FirstOrDefault() ?? string.Empty;
            var idx = line.IndexOf("version \"", StringComparison.OrdinalIgnoreCase);
            if (idx < 0) return 0;
            var versionText = line[(idx + 9)..];
            var end = versionText.IndexOf('"');
            if (end > 0) versionText = versionText[..end];
            var firstPart = versionText.Split('.', StringSplitOptions.RemoveEmptyEntries).FirstOrDefault();
            if (int.TryParse(firstPart, out var major)) return major == 1 ? 8 : major;
        }
        catch { }
        return 0;
    }

    private static string GetSha256(string filePath)
    {
        using var stream = File.OpenRead(filePath);
        return Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
    }

    private static void TryDeleteFile(string path)
    {
        try
        {
            if (File.Exists(path)) File.Delete(path);
        }
        catch { }
    }

    private static bool IsHttpUrl(string value) => value.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || value.StartsWith("https://", StringComparison.OrdinalIgnoreCase);
    private string ResolvePath(string path) => Path.IsPathRooted(path) ? path : Path.GetFullPath(Path.Combine(BaseDir, path));
    private static void EnsureDirectory(string path) { if (!Directory.Exists(path)) Directory.CreateDirectory(path); }
    private static T? ReadJsonFile<T>(string path) => !File.Exists(path) ? default : JsonSerializer.Deserialize<T>(File.ReadAllText(path), JsonOptions);
    private static void WriteJsonFile<T>(string path, T value) { EnsureDirectory(Path.GetDirectoryName(path)!); File.WriteAllText(path, JsonSerializer.Serialize(value, JsonOptions)); }
    private static string DefaultIfEmpty(string? value, string fallback) => string.IsNullOrWhiteSpace(value) ? fallback : value;

    private void SetBusy(bool busy)
    {
        _busy = busy;
        _progressBar.Visible = busy;
        if (!busy) _progressBar.Value = 0;
        _launchButton.Enabled = !busy;
    }

    private void SetStatus(string text)
    {
        if (InvokeRequired) { BeginInvoke(new Action<string>(SetStatus), text); return; }
        _statusLabel.Text = text;
    }

    private void SetProgress(int value)
    {
        var clamped = Math.Clamp(value, 0, 100);
        if (InvokeRequired) { BeginInvoke(new Action<int>(SetProgress), clamped); return; }
        _progressBar.Value = clamped;
    }
}
