package com.intershop.gradle.icm.docker.tasks.utils

import spock.lang.Specification

class DBPrepareProgressReporterSpec extends Specification {

    /** Characters of the progress bar, see the reporter. */
    private static final String DELIMITER = "\u2502"
    private static final String BLOCK = "\u2588"
    private static final String HALF_BLOCK = "\u258c"
    private static final String REMAINING = "\u00b7"


    def 'the counts, the cartridge and the step are shown'() {
        when:
        def rendered = DBPrepareProgressReporter.render(30, 813, 2707, "platform:core", "Class1 SQLScriptPreparer")

        then:
        rendered.startsWith(DELIMITER)
        rendered.endsWith("$DELIMITER 30% 813/2707 preparation steps - [platform:core] Class1 SQLScriptPreparer")
    }

    def 'the step description is omitted when neither cartridge nor step is known'() {
        expect:
        DBPrepareProgressReporter.render(50, 1, 2, cartridge, step).endsWith("$DELIMITER 50% 1/2 preparation steps")

        where:
        cartridge | step
        null      | null
        ""        | ""
    }

    def 'a known cartridge or step alone is shown'() {
        expect:
        DBPrepareProgressReporter.render(50, 1, 2, "platform:core", "")
                .endsWith("$DELIMITER 50% 1/2 preparation steps - [platform:core]")

        DBPrepareProgressReporter.render(50, 1, 2, "", "Class1 SQLScriptPreparer")
                .endsWith("$DELIMITER 50% 1/2 preparation steps - Class1 SQLScriptPreparer")
    }

    def 'the bar is filled proportionally to the progress'() {
        expect:
        // a position can be filled by half, so the filled ones are counted in halves
        filledHalves(percent) == (int) (barWidth() * 2 * percent / 100)

        where:
        percent << [0, 25, 36, 50, 93, 100]
    }

    def 'the bar is empty at the beginning and full at the end'() {
        expect:
        barOf(DBPrepareProgressReporter.render(0, 0, 2, null, null)) == REMAINING * barWidth()
        barOf(DBPrepareProgressReporter.render(100, 2, 2, null, null)) == BLOCK * barWidth()
    }

    def 'a half filled position is rendered as a half block'() {
        when:
        // 25% of 42 positions is 10.5 positions
        def bar = barOf(DBPrepareProgressReporter.render(25, 1, 4, null, null))

        then:
        bar.count(BLOCK) == 10
        bar.count(HALF_BLOCK) == 1
    }

    def 'the bar keeps its width regardless of the progress'() {
        expect:
        // the total is only an estimation, so more steps than estimated may be reported
        barOf(DBPrepareProgressReporter.render(percent, 1, 2, null, null)).length() == barWidth()

        where:
        percent << [-10, 0, 50, 100, 150]
    }

    private static int filledHalves(int percent) {
        def bar = barOf(DBPrepareProgressReporter.render(percent, 1, 2, null, null))
        return 2 * bar.count(BLOCK) + bar.count(HALF_BLOCK)
    }

    private static int barWidth() {
        return barOf(DBPrepareProgressReporter.render(0, 0, 1, null, null)).length()
    }

    private static String barOf(String rendered) {
        return rendered.substring(rendered.indexOf(DELIMITER) + 1, rendered.lastIndexOf(DELIMITER))
    }
}
