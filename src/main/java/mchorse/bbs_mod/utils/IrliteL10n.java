package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSSettings;

import java.util.HashMap;
import java.util.Map;

/** Russian for the strings the IRLights addon hardcodes with
 *  {@code IKey.constant(...)} in its settings UI — they never reach BBS's
 *  language files, so the Irlite*L10nMixin classes intercept those exact
 *  calls and swap the label here when the game language is Russian.
 *
 *  <p>Every key below is an exact string literal from the addon's current
 *  source; a key that stops matching (addon update) simply falls through
 *  and shows the English again — nothing breaks. Dynamic strings (pack and
 *  patch names embedded in status lines) are intentionally not mapped.</p>
 */
public final class IrliteL10n
{
    private static final Map<String, String> RU = new HashMap<>();

    static
    {
        /* UIPresetSection: the two preset rows drawn at the top of the page. */
        RU.put("Presets", "Пресеты");
        RU.put("Quality", "Качество");
        RU.put("Beam style", "Стиль лучей");
        RU.put("Performance", "Низкое");
        RU.put("Balanced", "Сбалансированное");
        RU.put("Ultra", "Ультра");
        RU.put("Custom", "Свой");
        RU.put("Clean", "Чистый");
        RU.put("Dusty", "Пыльный");
        RU.put("Smoky", "Дымный");
        RU.put("Cost of the lighting: march steps, ray distance, shadow and noise tap strides, shadow map resolution and the shader light cap. Custom means the knobs below no longer match any preset — pick one to overwrite them. No preset selects ULTRA shadows; that one stays a deliberate choice.",
            "Стоимость освещения: шаги трассировки, дальность лучей, шаг выборки теней и шума, разрешение карт теней и лимит источников в шейдере. «Свой» означает, что настройки ниже больше не совпадают ни с одним пресетом — выберите пресет, чтобы перезаписать их. Ни один пресет не включает тени ULTRA — это остаётся осознанным выбором.");
        RU.put("Look of the volumetric beams: noise, drift and the glow around the lamp itself. Clean is uniform beams, Dusty is drifting puffs, Smoky is heavy morphing haze (the priciest of the three — it is the only one that turns morph on).",
            "Вид объёмных лучей: шум, дрейф и свечение вокруг самой лампы. «Чистый» — ровные лучи, «Пыльный» — дрейфующие сгустки, «Дымный» — плотный морфингующий дым (самый дорогой из трёх — единственный, кто включает морфинг).");

        /* IrlightsAddon: mode labels of the two int settings
         * (shadow_quality and outline_target). */
        RU.put("LOW", "НИЗКОЕ");
        RU.put("MEDIUM", "СРЕДНЕЕ");
        RU.put("HIGH", "ВЫСОКОЕ");
        RU.put("ULTRA", "УЛЬТРА");
        RU.put("ALL", "ВСЕ");
        RU.put("ENTITIES", "СУЩНОСТИ");
        RU.put("BLOCKS", "БЛОКИ");

        /* UIPatcherSection: the shader patcher page. */
        RU.put("Shaderpacks", "Шейдерпаки");
        RU.put("Refresh lists", "Обновить списки");
        RU.put("Open shaderpacks folder", "Открыть папку шейдерпаков");
        RU.put("Patches", "Патчи");
        RU.put("Open patches folder", "Открыть папку патчей");
        RU.put("Create new pack each time", "Создавать новый пак каждый раз");
        RU.put("Validate", "Проверить");
        RU.put("Dry-run: check every op against the selected pack, write nothing", "Пробный прогон: проверить все операции по выбранному паку, ничего не записывать");
        RU.put("Patch", "Применить");
        RU.put("Select a shaderpack and a patch for it.", "Выберите шейдерпак и патч для него.");
        RU.put("Couldn't read this patch.", "Не удалось прочитать этот патч.");
        RU.put("Select a shaderpack above to continue.", "Выберите шейдерпак выше, чтобы продолжить.");
        RU.put("Select a shaderpack from the list.", "Выберите шейдерпак из списка.");
        RU.put("Select a patch for the shaderpack.", "Выберите патч для шейдерпака.");
        RU.put("Couldn't read the selected patch.", "Не удалось прочитать выбранный патч.");
        RU.put("It fits! Press Patch to create the light version of the pack.", "Совпадает! Нажмите «Применить», чтобы создать освещённую версию пака.");
        RU.put("This shaderpack already has the light. Pick the original (clean) pack.", "Этот шейдерпак уже пропатчен. Выберите оригинальный (чистый) пак.");
        RU.put("Patch isn't compatible with this mod version. Update the mod or the patch.", "Патч несовместим с этой версией мода. Обновите мод или патч.");
        RU.put("Couldn't open the shaderpack. Make sure a valid pack is selected.", "Не удалось открыть шейдерпак. Убедитесь, что выбран корректный пак.");
        RU.put("File error. Close the pack in other programs and try again.", "Ошибка файла. Закройте пак в других программах и попробуйте снова.");
        RU.put("This patch didn't fit the selected pack, maybe it's a different version.", "Патч не подошёл к выбранному паку, возможно, это другая версия.");
    }

    private IrliteL10n()
    {}

    /** Temporary diagnostics: how many intercepted calls to print, to find out
     *  whether the interception fires at all and which exact strings the
     *  installed addon jar actually passes. Remove once the translation works. */
    private static final int DIAG_LIMIT = 40;

    private static int diagCount = 0;

    public static String translate(String label)
    {
        if (label == null)
        {
            return null;
        }

        String lang;

        try
        {
            lang = BBSSettings.language.get();
        }
        catch (Throwable t)
        {
            lang = "<unreadable: " + t + ">";
        }

        String ru = "ru_ru".equals(lang) ? RU.get(label) : null;

        if (diagCount++ < DIAG_LIMIT)
        {
            System.out.println("[IrliteL10n] lang=\"" + lang + "\" label=\"" + label + "\" -> "
                + (ru != null ? "RU" : "EN"));
        }

        return ru != null ? ru : label;
    }
}
