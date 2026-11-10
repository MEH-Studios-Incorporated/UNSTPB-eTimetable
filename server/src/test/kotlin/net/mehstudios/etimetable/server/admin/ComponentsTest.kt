// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.parametersOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentsTest {
    @Test
    fun pagingClampsThePageAndTheSize() {
        val paging = parametersOf("page" to listOf("9"), "size" to listOf("7")).paging(total = 23)
        assertEquals(10, paging.size)
        assertEquals(3, paging.page)
        assertEquals(20, paging.offset)
        assertEquals(listOf(21, 22, 23), paging.slice((1..23).toList()))
        assertEquals(1, parametersOf().paging(total = 0).page)
    }

    @Test
    fun pageNumbersKeepTheEndsAndTheNeighbours() {
        fun numbers(page: Int, pages: Int) = pageNumbers(Paging(page, 10, pages * 10))
        assertEquals(listOf(1, 2, 3, null, 20), numbers(2, 20))
        assertEquals(listOf(1, null, 9, 10, 11, null, 20), numbers(10, 20))
        // a single hidden page is shown instead of "..."
        assertEquals(listOf(1, 2, 3, 4, 5), numbers(4, 5))
        assertEquals(listOf(1), numbers(1, 1))
    }

    @Test
    fun linksKeepFiltersAndDropMessages() {
        val query = parametersOf("q" to listOf("ana"), "page" to listOf("3"), "msg" to listOf("Salvat"))
        assertEquals("/admin/students?q=ana&page=4", linkTo("/admin/students", query, mapOf("page" to "4")))
        assertEquals("/admin/students?q=ana", linkTo("/admin/students", query, mapOf("page" to null)))
    }
}
