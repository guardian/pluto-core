interface Commission {
  id: number;
  title: string;
  projectCount: number;
  created: string;
  workingGroupId: number;
  status: string;
  owner: string;
}

interface CommissionFullRecord {
  id: number;
  title: string;
  projectCount: number;
  created: string;
  workingGroupId: number;
  status: string;
  owner: string;
  collectionId?: number;
  siteId?: number;
  updated: string;
  description?: string;
  originalCommissionerName?: string;
  scheduledCompletion: string;
  notes?: string;
  productionOffice: string;
  originalTitle: string;
}
/*
    (JsPath \ "id").writeNullable[Int] and
      (JsPath \ "collectionId").writeNullable[Int] and
      (JsPath \ "siteId").writeNullable[String] and
      (JsPath \ "created").write[Timestamp] and
      (JsPath \ "updated").write[Timestamp] and
      (JsPath \ "title").write[String] and
      (JsPath \ "status").write[EntryStatus.Value] and
      (JsPath \ "description").writeNullable[String] and
      (JsPath \ "workingGroupId").write[Int] and
      (JsPath \ "originalCommissionerName").writeNullable[String] and
      (JsPath \ "scheduledCompletion").write[Timestamp] and
      (JsPath \ "owner").write[String] and
      (JsPath \ "notes").writeNullable[String] and
      (JsPath \ "productionOffice").write[ProductionOffice.Value] and
      (JsPath \ "originalTitle").writeNullable[String]
 */
interface CreateWorkingGroup {
  name: string;
  commissioner: string;
}

interface WorkingGroup extends CreateWorkingGroup {
  id: number;
  hide: boolean;
}

type ProductionOffice = "UK" | "US" | "Aus";
type ProjectStatus = "New" | "In Production" | "Held" | "Completed" | "Killed";

interface Project {
  id: number;
  projectTypeId: number;
  title: string;
  created: string;
  user: string;
  workingGroupId: number;
  commissionId: number;
  deletable: boolean;
  deep_archive: boolean;
  sensitive: boolean;
  status: ProjectStatus;
  productionOffice: ProductionOffice;
}

type FilterOrderType = "W_STARTSWITH" | "W_ENDSWITH" | "W_CONTAINS" | "W_EXACT";

interface FilterTerms {
  match?: FilterOrderType;
  commissionId?: number;
  user?: string;
}

interface PlutoUser {
  uid: string;
  isAdmin: boolean;
}

interface PlutoApiResponse<T> {
  result: T;
}

interface ProjectMetadataResponse {
  id: number;
  projectEntryRef: number;
  key: string;
  value: string;
}

interface HeaderTitle<T> {
  label: string;
  key?: keyof T;
}

interface ProjectType {
  id: number;
  name: string;
  opensWith: string;
  targetVersion: string;
  fileExtension?: string;
  plutoType?: string;
}

declare module "*.svg" {
  const content: any;
  export default content;
}

declare module "*.svg" {
  import React = require("react");
  export const ReactComponent: React.FC<React.SVGProps<SVGSVGElement>>;
  const src: string;
  export default src;
}

interface Deliverable {
  id: bigint;
  type: number | null;
  filename: string;
  size: bigint;
  access_dt: string;
  modified_dt: string;
  changed_dt: string;
  job_id: string | null;
  item_id: string | null;
  deliverable: bigint;
  has_ongoing_job: boolean | null;
  status: bigint;
  type_string: string | null;
  version: bigint | null;
  duration: string | null;
  size_string: string;
  status_string: string;
}

interface DeliverablesCount {
  total_asset_count: number;
  unimported_asset_count: number;
}
