package mess

import java.time.ZonedDateTime

/**
 * message format that is used for `project.update`
 * on the pluto-deliverables exchange
 */
case class ProjectUpdateMessage(id: Int,
                                projectTypeId: Int,
                                title: String,
                                created: Option[ZonedDateTime],
                                updated: Option[ZonedDateTime],
                                user: String,
                                workingGroupId: Int,
                                commissionId: Int,
                                deletable: Boolean,
                                deep_archive: Boolean,
                                sensitive: Boolean,
                                status: String,
                                productionOffice: String)
