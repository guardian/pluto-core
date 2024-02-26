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
  googleFolder: string;
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
type ProjectStatus = "New" | "In Production" | "Held" | "Completed" | "Killed";

interface Project {
  id: number;
  projectTypeId: number;
  title: string;
  created: string;
  user: string;
  workingGroupId: number;
  isObitProject?: string | null;
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
  showKilled?: boolean | string;
}
interface ProjectFilterTerms extends FilterTerms {
  title?: string;
  group?: string;
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

interface ProjectTemplate {
  id: number;
  name: string;
  projectTypeId: number;
  fileRef: number;
  deprecated?: boolean;
}

interface PlutoDefault {
  id: number;
  name: string;
  value: string;
}

interface ProjectFilesResponse {
  status: string;
  files: FileEntry[];
}

interface FileMetadataResponse {
  status: string;
  metadata: Map<string, string>;
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

interface DeliverableBundle {
  total_assets: number;
  project_id: string;
  pluto_core_project_id: number;
  commission_id: number;
  name: string;
  created: string;
  local_open_uri: string;
  local_path: string;
}

interface VaultDescription {
  vaultId: string;
  name: string;
}

interface PostrunAction {
  id: number;
  runnable: string;
  title: string;
  description?: string;
  owner: string;
  version: number;
  ctime: string;
}

interface ObjectListResponse<T> {
  status: string;
  count: number;
  result: T[];
}

type PostrunActionsResponse = ObjectListResponse<PostrunAction>;

type PlutoStorageStatus =
  | "ONLINE"
  | "OFFLINE"
  | "DISAPPEARED"
  | "MISCONFIGURED"
  | "UNKNOWN";

interface PlutoStorage {
  id: number;
  nickname?: string;
  rootpath?: string;
  clientpath?: string;
  storageType: string;
  user?: string;
  device?: string;
  supportsVersions: boolean;
  status?: PlutoStorageStatus;
}

type PlutoStorageListResponse = ObjectListResponse<PlutoStorage>;

interface ProjectCreatedResponse {
  status: string;
  detail: string;
  projectId: number;
}

interface GenericCreateResponse {
  status: string;
  detail: string;
  id: number;
}

interface GenericErrorResponse {
  status: string;
  detail?: string;
}

interface StorageType {
  name: string;
  needsLogin: boolean;
  hasSubFolders: boolean;
  canVersion: boolean;
}

interface StorageTypeResponse {
  status: string;
  types: StorageType[];
}

interface StorageLoginDetails {
  hostname: string;
  port: number;
  device: string;
  username: string;
  password: string;
}

interface StorageEntry {
  id: number;
  nickname?: string;
  rootpath?: string;
  clientpath?: string;
  storageType: string;
  user?: string;
  password?: string;
  host?: string;
  port?: number;
  device?: string;
  supportsVersion: boolean;
  status?: string;
  backsUpTo?: number;
}

interface ValidationJob {
  uuid: string;
  userName: string;
  jobType: string;
  startedAt?: string;
  completedAt?: string;
  status: string;
  errorMessage?: string;
}

type ValidationJobColumn =
  | "uuid"
  | "userName"
  | "jobType"
  | "startedAt"
  | "completedAt"
  | "status"
  | "errorMessage";

interface ValidationJobListResponse {
  status: string;
  totalCount: number;
  jobs: ValidationJob[];
}

interface ValidationProblem {
  job: string;
  timestamp: string;
  entityClass: string;
  entityId: number;
  notes?: string;
}

interface ValidationProblemListResponse {
  status: string;
  totalCount: number;
  entries: ValidationProblem[];
}

type ValidationScanType =
  | "CheckAllFiles"
  | "CheckSomeFiles"
  | "MislinkedPTR"
  | "UnlinkedProjects"
  | "UnlinkedFiles"
  | "UnlinkedFilesWithBlanks";

interface ValidationRequestDoc {
  validationType: ValidationScanType;
}

interface FileEntry {
  id: number;
  filepath: string;
  storage: number;
  user: string;
  version: number;
  ctime: string;
  mtime: string;
  atime: string;
  hasContent: boolean;
  hasLink: boolean;
  backupOf?: number;
  premiereVersion?: number;
}

interface PremiereVersionTranslation {
  internalVersionNumber: number;
  name: string;
  displayedVersion: string;
}

type PremiereVersionTranslationResponse = ObjectListResponse<
  PremiereVersionTranslation
>;

interface ConversionResponse {
  status: string;
  detail: string;
  entry: FileEntry;
}

interface FileEntryFilterTerms {
  filePath?: string;
  match: string;
  storageId?: number;
}

interface PlutoApiResponseWithCount<T> {
  result: T;
  count: number;
}

interface ItemsNotDeleted {
  id?: number;
  projectEntry?: number;
  item?: string;
}

interface AssetFolderFileEntry {
  id: number;
  filepath: string;
  storage: number;
  version: number;
  ctime: string;
  mtime: string;
  atime: string;
  backupOf?: number;
}

interface AssetFolderProjectFilesResponse {
  status: string;
  files: AssetFolderFileEntry[];
}

interface DeletionRecord {
  id: number;
  projectEntry: number;
  status: string;
}