package com.je.fontsmanager.samsung.util

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * 基于 pinyin4j 的中文转拼音工具类，覆盖全部 CJK 汉字。
 */
object PinyinUtils {

    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    /**
     * 将显示名称转换为安全的 ASCII 字符串（驼峰式拼音 + 保留英文数字）。
     *
     * 示例：
     * - "楷体"       → "KaiTi"
     * - "人偶仿宋"   → "RenOuFangSong"
     * - "思源黑体"   → "SiYuanHeiTi"
     * - "Roboto"     → "Roboto"
     * - "思源Bold"   → "SiYuanBold"
     * - ""           → "UntitledFont"
     */
    fun toSafeAsciiName(displayName: String): String {
        if (displayName.isBlank()) return "UntitledFont"

        val sb = StringBuilder()
        for (ch in displayName) {
            when {
                isChinese(ch) -> {
                    try {
                        val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(ch, format)
                        if (!pinyinArray.isNullOrEmpty()) {
                            val py = pinyinArray[0] // 取第一个读音
                            // 首字母大写驼峰
                            sb.append(py[0].uppercaseChar())
                            if (py.length > 1) {
                                sb.append(py.substring(1))
                            }
                        }
                    } catch (_: Exception) {
                        // 无法转换的字符静默跳过
                    }
                }
                ch.isLetterOrDigit() && ch.code < 128 -> {
                    sb.append(ch)
                }
                // 忽略其他字符（空格、标点等）
            }
        }

        val result = sb.toString()
        return result.ifEmpty { "UntitledFont" }
    }

    /** 判断字符是否为 CJK 统一汉字 */
    private fun isChinese(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF
            || code in 0x3400..0x4DBF
            || code in 0xF900..0xFAFF
    }
}
