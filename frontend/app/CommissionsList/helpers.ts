import Axios from "axios";
import { getProjectsOnPage } from "../ProjectEntryList/helpers";

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
  filterTerms?: FilterTerms;
  order?: string;
  orderBy?: string | number | symbol;
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
  order,
  orderBy,
}: GetCommissionsOnPageParams): Promise<Commission[]> => {
  const itemOffset = page * pageSize;
  const {
    status,
    data: { result: commissions = [] },
  } = await (filterTerms
    ? // TODO: filter terms formatting?
      Axios.put(
        `${API_COMMISSION_FILTER}?startAt=${itemOffset}&length=${pageSize}&sort=${String(
          orderBy
        )}&sortDirection=${order}`,
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

export const projectsForCommission: (
  commissionId: number,
  page: number,
  pageSize: number,
  filterTerms: ProjectFilterTerms,
  order: string,
  orderBy: string | number | symbol
) => Promise<[Project[], number]> = async (
  commissionId: number,
  page: number,
  pageSize: number,
  filterTerms: ProjectFilterTerms,
  order: string,
  orderBy: string | number | symbol
) => {
  filterTerms["commissionId"] = commissionId;
  return getProjectsOnPage({
    page: page,
    pageSize: pageSize,
    filterTerms: filterTerms,
    order: order,
    orderBy: orderBy,
  });
};

export const startDelete = async (
  id: number,
  commission: boolean,
  pluto: boolean,
  file: boolean,
  backups: boolean,
  pTR: boolean,
  deliverables: boolean,
  sAN: boolean,
  matrix: boolean,
  s3: boolean,
  buckets: string[],
  bucketBooleans: boolean[]
): Promise<void> => {
  try {
    let bucketsArray = `[`;
    for (const bucket of buckets) {
      bucketsArray = bucketsArray + `"${bucket}",`;
    }
    bucketsArray = bucketsArray.slice(0, -1);
    bucketsArray = bucketsArray + `]`;
    let booleansArray = `[`;
    for (const boolean of bucketBooleans) {
      booleansArray = booleansArray + `${boolean},`;
    }
    booleansArray = booleansArray.slice(0, -1);
    booleansArray = booleansArray + `]`;
    const { status } = await Axios.put<PlutoApiResponse<void>>(
      `${API_COMMISSION}/${id}/deleteData`,
      `{"commission":${commission},"pluto":${pluto},"file":${file},"backups":${backups},"PTR":${pTR},"deliverables":${deliverables},"SAN":${sAN},"matrix":${matrix},"S3":${s3},"buckets":${bucketsArray},"bucketBooleans":${booleansArray}}`,
      {
        headers: {
          "Content-Type": "application/json",
        },
      }
    );

    if (status !== 200) {
      throw new Error(
        `Could not start deletion of data for commission ${id}: server said ${status}`
      );
    }
  } catch (error) {
    console.error(error);
    throw error;
  }
};
