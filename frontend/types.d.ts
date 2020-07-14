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

interface FilterTerms {
  match?: string;
  user?: string;
}

interface IsLoggedIn {
  isAdmin: boolean;
  uid: string;
}

interface PlutoApiResponse<T> {
  result: T;
}
