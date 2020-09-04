interface Commission {
  id: number;
  title: string;
  projectCount: number;
  created: number;
  workingGroupId: number;
  status: string;
  owner: string;
}

interface CreateWorkingGroup {
  name: string;
  commissioner: string;
}

interface WorkingGroup extends CreateWorkingGroup {
  id: number;
  hide: boolean;
}

type ProductionOffice = "UK" | "US" | "Aus";
type ProjectStatus = "New" | "In Production";

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
