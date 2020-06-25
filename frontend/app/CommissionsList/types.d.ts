interface Commission {
  id: number;
  title: string;
  projectCount: number;
  created: number;
  workingGroupId: number;
  status: string;
  owner: string;
}

interface WorkingGroup {
  name: string;
}

// TODO: probably move this to a more general place in the app directory
// hierarchy, since it should be general.
interface PlutoApiResponse<T> {
  result: T;
}
