interface CreateWorkingGroup {
  name: string;
  commissioner: string;
}

interface WorkingGroup extends CreateWorkingGroup {
  id: number;
}

interface PlutoApiResponse<T> {
  result: T;
}
