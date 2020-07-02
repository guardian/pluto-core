interface CreateWorkingGroup {
  name: string;
  commissioner: string;
}

interface WorkingGroup extends CreateWorkingGroup {
  id: number;
}

interface Project {
  id: number;
  projectTypeId: number;
  title: string;
  created: string;
  user: string;
  workingGroupId: number;
  commissionId: number;
  deletable: false;
  deep_archive: true;
  sensitive: false;
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
