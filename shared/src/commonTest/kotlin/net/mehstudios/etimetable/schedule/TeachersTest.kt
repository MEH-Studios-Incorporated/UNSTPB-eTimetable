// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import net.mehstudios.etimetable.SampleData
import net.mehstudios.etimetable.model.Series
import kotlin.test.Test
import kotlin.test.assertEquals

class TeachersTest {
    private val sample = SampleData.dataset

    // the sample's quality course has no professor yet; give it one
    private val dataset = sample.copy(
        activities = sample.activities.map { if (it.id == "calit-c") it.copy(professorId = "marinescu") else it },
    )

    @Test
    fun aLabReachesItsSeriesThroughTheHalfGroup() {
        assertEquals(setOf("e4"), dataset.seriesOf(SampleData.activity("calit-l-1542B")))
    }

    @Test
    fun aLabNamesTheCourseProfessor() {
        val lab = dataset.activitiesById.getValue("calit-l-1541A")
        assertEquals(listOf("marinescu"), dataset.courseProfessors(lab).map { it.id })
    }

    @Test
    fun aCourseHasNoSeparateCourseProfessor() {
        assertEquals(emptyList(), dataset.courseProfessors(dataset.activitiesById.getValue("calit-c")))
    }

    @Test
    fun anotherSeriesCourseDoesNotCount() {
        val other = dataset.copy(
            series = dataset.series + Series("e3", "iedeee", 3, "E"),
            activities = dataset.activities.map {
                if (it.id == "calit-c") it.copy(audience = it.audience.copy(seriesIds = setOf("e3"))) else it
            },
        )
        assertEquals(emptyList(), other.courseProfessors(other.activitiesById.getValue("calit-l-1541A")))
    }

    @Test
    fun aSubjectWithoutAProfessorGivesNobody() {
        assertEquals(emptyList(), sample.courseProfessors(SampleData.activity("calit-l-1541A")))
    }
}
