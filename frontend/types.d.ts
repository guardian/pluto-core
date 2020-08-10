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
  status: string;
  productionOffice: string;
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
