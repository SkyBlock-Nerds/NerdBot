using MinecraftRenderer;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Formats.Png;

var dataPath = @"{{DATA_PATH}}";
var outputPath = @"{{OUTPUT_PATH}}";
var size = {{SIZE}};

Console.WriteLine($"Loading renderer from: {dataPath}");
using var renderer = MinecraftBlockRenderer.CreateFromDataDirectory(dataPath);

var options = MinecraftBlockRenderer.BlockRenderOptions.Default with { Size = size };
// Force RGBA output
var encoder = new PngEncoder { ColorType = PngColorType.RgbWithAlpha, SkipMetadata = true };

Directory.CreateDirectory(outputPath);

var knownItems = renderer.GetKnownItemNames();
var knownBlocks = renderer.GetKnownBlockNames();
Console.WriteLine($"Found {knownItems.Count} known items and {knownBlocks.Count} known blocks");

// Skip armor trim variants
var armorTypes = new[] { "helmet", "chestplate", "leggings", "boots" };

// Load trim materials from color palettes directory
var trimPalettesPath = Path.Combine(dataPath, "textures", "trims", "color_palettes");
var trimMaterials = Directory.Exists(trimPalettesPath)
    ? Directory.GetFiles(trimPalettesPath, "*.png")
        .Select(f => Path.GetFileNameWithoutExtension(f))
        .Where(n => n != "trim_palette")
        .ToArray()
    : Array.Empty<string>();

Console.WriteLine($"Loaded {trimMaterials.Length} trim materials from assets");

bool IsArmorTrimVariant(string name) =>
    name.EndsWith("_trim") && trimMaterials.Length > 0 &&
    armorTypes.Any(armor => trimMaterials.Any(mat => name.EndsWith($"{armor}_{mat}_trim")));

var filteredItems = knownItems.Where(item => !IsArmorTrimVariant(item));
var filteredCount = knownItems.Count - filteredItems.Count();

var allNames = filteredItems
    .Concat(knownBlocks)
    .Distinct(StringComparer.OrdinalIgnoreCase)
    .OrderBy(n => n)
    .ToList();

if (filteredCount > 0)
    Console.WriteLine($"Filtered out {filteredCount} armor trim variants (rendered dynamically)");

Console.WriteLine($"Total unique names to render: {allNames.Count}");
Console.WriteLine();

const int barWidth = 30;

void PrintProgress(int current, int total, string item)
{
    var pct = (double)current / total;
    var filled = (int)(pct * barWidth);
    var bar = new string('█', filled) + new string('░', barWidth - filled);
    var displayName = item.Length > 25 ? item[..23] + ".." : item;
    Console.Write($"\r[{bar}] {pct * 100,5:F1}% | {current}/{total} | {displayName,-25}");
}

int rendered = 0, failed = 0;

for (var i = 0; i < allNames.Count; i++)
{
    var name = allNames[i];
    var outputFile = Path.Combine(outputPath, $"{name}.png");

    try
    {
        using var image = renderer.RenderItem(name, options);
        image.Save(outputFile, encoder);
        rendered++;
    }
    catch (Exception ex)
    {
        failed++;
        Console.WriteLine($"\nFailed to render {name}: {ex.Message}");
    }

    PrintProgress(i + 1, allNames.Count, name);
}

Console.WriteLine($"\n\n[{new string('█', barWidth)}] 100.0% | Complete!\n");
Console.WriteLine($"Rendered: {rendered} | Failed: {failed}");