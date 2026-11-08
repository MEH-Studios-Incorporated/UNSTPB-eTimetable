// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import kotlinx.serialization.Serializable

/** A correction request for one recurring class in the current semester. */
@Serializable
data class TimetableReportRequest(val semesterId: String, val activityId: String, val description: String)

@Serializable
data class TimetableReportReceipt(val id: String)

const val MIN_REPORT_DESCRIPTION_LENGTH = 10
const val MAX_REPORT_DESCRIPTION_LENGTH = 2000
