package com.buddy.budget.i18n

import com.buddy.budget.model.Category
import com.buddy.budget.portfolio.model.Frequency
import com.buddy.budget.portfolio.model.Glossary
import com.buddy.budget.portfolio.model.InputMode
import com.buddy.budget.portfolio.model.LookbackUnit
import com.buddy.budget.portfolio.model.PeriodMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TranslationTest {

    private val literal = """"((?:[^"\\]|\\.)*)""""
    private val plainCall = Regex("""tr\($literal\)""")
    private val conditionalCall = Regex("""tr\(if \(.*?\) $literal else $literal\)""")
    // The flags the app uses; a space flag is left out so "% of outflows" is not read as a specifier.
    private val formatSpecifier = Regex("""%[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z%]""")

    /** Every key passed to tr() in the app's source, unescaped, plus the enum labels. */
    private fun keys(): Set<String> {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.toList()
        val fromCalls = sources.flatMap { source ->
            plainCall.findAll(source).map { it.groupValues[1] } +
                conditionalCall.findAll(source).flatMap { sequenceOf(it.groupValues[1], it.groupValues[2]) }
        }.map { it.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n") }
        val enumLabels = Category.entries.map { it.englishLabel } + LookbackUnit.entries.map { it.englishLabel } +
            Frequency.entries.map { it.englishLabel } + PeriodMode.entries.map { it.englishLabel } + InputMode.entries.map { it.englishLabel }
        return (fromCalls + enumLabels).toSet()
    }

    @After
    fun backToEnglish() {
        AppLanguage.current = Language.EN
    }

    @Test
    fun everyKeyHasAPortugueseTranslation() {
        val missing = keys().filter { it !in portuguese }
        assertTrue("Missing Portuguese for:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun translationsKeepTheFormatSpecifiers() {
        val mismatched = portuguese.filter { (english, translated) ->
            formatSpecifier.findAll(english).map { it.value }.toList() != formatSpecifier.findAll(translated).map { it.value }.toList()
        }
        assertTrue("Format specifiers differ:\n" + mismatched.keys.joinToString("\n"), mismatched.isEmpty())
    }

    @Test
    fun noUnusedTranslations() {
        val unused = portuguese.keys - keys()
        assertTrue("Unused Portuguese entries:\n" + unused.joinToString("\n"), unused.isEmpty())
    }

    @Test
    fun trFollowsTheLanguage() {
        assertEquals("Save", tr("Save"))
        val englishGlossary = Glossary.parameters.size to Glossary.metrics.size
        AppLanguage.current = Language.PT
        assertEquals(portuguese.getValue("Save"), tr("Save"))
        assertEquals("Moradia", Category.HOUSING.label)
        assertEquals(englishGlossary, Glossary.parameters.size to Glossary.metrics.size)
    }
}
