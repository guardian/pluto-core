package services.migrationcomponents

import models.{FileEntry, ProjectEntry}

case class ProjectFileResult(projectEntry:ProjectEntry, filePaths:Seq[String], fileEntries:Option[Seq[FileEntry]]=None)
