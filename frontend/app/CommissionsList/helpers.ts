import Axios from "axios";

const API = "/api/pluto";
const API_COMMISSION = `${API}/commission`;
const API_COMMISSION_FILTER = `${API_COMMISSION}/list`;
const API_WORKING_GROUP = `${API}/workinggroup`;

/**
 * Returns a Map of working group IDs -> working group names
 *
 * @param commissions a list of Commission objects
 */
export const getWorkingGroupNameMap = async (
  commissions: Commission[]
): Promise<Map<number, string>> =>
  new Map(
    await Promise.all(
      commissions.map(async (commission) => {
        try {
          const {
            status,
            data: {
              result: { name: workingGroupName },
            },
          } = await Axios.get<PlutoApiResponse<WorkingGroup>>(
            `${API_WORKING_GROUP}/${commission.workingGroupId}`
          );

          if (status === 200) {
            return [commission.workingGroupId, workingGroupName] as const;
          } else {
            throw new Error(
              `could not fetch working group name (HTTP status: ${status})`
            );
          }
        } catch (error) {
          console.error(
            "could not fetch working group name for WG with id:",
            commission.workingGroupId
          );
          throw error;
        }
      })
    )
  );

interface GetCommissionsOnPageParams {
  page?: number;
  pageSize?: number;
  filterTerms?: string[];
}

/**
 * Returns the commissions for a particular pagination offset.
 *
 * @param params parameters to support pagination
 */
export const getCommissionsOnPage = async ({
  page = 0,
  pageSize = 25,
  filterTerms,
}: GetCommissionsOnPageParams): Promise<Commission[]> => {
  const itemOffset = page * pageSize;
  const {
    status,
    data: { result: commissions = [] },
  } = await (filterTerms
    ? // TODO: filter terms formatting?
      Axios.put(
        `${API_COMMISSION_FILTER}?startAt=${itemOffset}&length=${pageSize}`,
        filterTerms
      )
    : Axios.get(`${API_COMMISSION}?startAt=${itemOffset}&length=${pageSize}`));

  if (status !== 200) {
    throw new Error("unable to fetch commissions");
  }

  return commissions;
};

// TODO: for use later?
// const loadDependencies = async ({ setIsAdmin, setUid }) => {
//   try {
//     const response = await Axios.get("/api/isLoggedIn");
//     if (response.data.status !== "ok") {
//       return;
//     }
//
//     setIsAdmin(response.data.isAdmin);
//     setUid(response.data.uid);
//   } catch (error) {
//     setIsAdmin(false);
//
//     if (response?.data?.status === 403) {
//       // 403 -- simply no access, not necessarily an "error".
//       return;
//     }
//
//     throw error;
//   }
// };

export const loadCommissionData: (
  id: number
) => Promise<CommissionFullRecord> = async (commissionId: number) => {
  const url = `/api/pluto/commission/${commissionId}`;
  const response = await Axios.get(url);
  //if response is not a 200 then we don't get here, an exception has been thrown. Caller should catch this failed promise.
  return response.data.result as CommissionFullRecord;
};

export const updateCommissionData: (
  record: CommissionFullRecord
) => Promise<void> = async (record: CommissionFullRecord) => {
  const url = `/api/pluto/commission/${record.id}`;
  const response = await Axios.put(url, record);
  return;
};
