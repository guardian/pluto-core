import axios from "axios";

async function getValidationRecords(
  username?: string,
  status?: string
): Promise<ValidationJobListResponse> {
  const args = [
    { key: "userName", value: username },
    { key: "status", value: status },
  ]
    .filter((entry) => !!entry.value)
    .map((entry) => `${entry.key}=${entry.value}`);

  const url =
    args.length > 0 ? `/api/validation?${args.join("&")}` : "/api/validation";

  const response = await axios.get<ValidationJobListResponse>(url);
  switch (response.status) {
    case 200:
      return response.data;
    default:
      console.log(
        `Could not get validation records - server returned ${response.status}: `,
        response.data
      );
      throw `Server returned an error`;
  }
}

export { getValidationRecords };
