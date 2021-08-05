import axios from "axios";
import { VidispineItem } from "./vidispine/item/VidispineItem";
import { VError } from "ts-interface-checker";

/**
 * validates a given vidispine item, returning either a VidispineItem or undefined if it fails to validate.
 * error message is output to console if it fails.
 * @param content object to verify
 */
const validateVSItem = (content: any) => {
  try {
    return new VidispineItem(content);
  } catch (err) {
    if (err instanceof VError) {
      const vErr = err as VError;

      const itemId = content.id ?? "(no id given)";
      console.error(
        `Item ${itemId} failed metadata validation at ${vErr.path}: ${vErr.message}`
      );
    } else {
      console.error("Unexpected error: ", err);
    }
    return undefined;
  }
};

interface AssetsResponse {
  items: VidispineItem[];
  totalCount: number;
}

function getHitCount(data: any, defaultValue: number): number {
  if (data.hits && typeof data.hits == "number") {
    return data.hits as number;
  } else {
    console.log("invalid hit count: ", data.hits);
    return defaultValue;
  }
}

async function assetsForProject(
  vidispineBaseUrl: string,
  projectId: number,
  fromCount: number,
  pageSize: number
): Promise<AssetsResponse> {
  const standardSearchDocument = `
<ItemSearchDocument xmlns="http://xml.vidispine.com/schema/vidispine">
    <field>
        <name>gnm_containing_projects</name>
        <value>"${projectId}"</value>
    </field>
    <sort>
        <field>durationSeconds</field>
        <order>descending</order>
    </sort>
</ItemSearchDocument>
    `;

  const fieldList = [
    "title",
    "representativeThumbnailNoAuth",
    "gnm_category",
  ].join(",");

  const response = await axios.put(
    `${vidispineBaseUrl}/API/item?content=metadata&field=${fieldList}&first=${fromCount}&number=${pageSize}&count=true`,
    standardSearchDocument,
    {
      headers: {
        "Content-Type": "application/xml",
        Accept: "application/json",
      },
      validateStatus: () => true,
    }
  );

  switch (response.status) {
    case 200:
      const hitcount = getHitCount(response.data, 0);
      if (response.data.item) {
        return {
          totalCount: hitcount,
          items: response.data.item
            .map(validateVSItem)
            .filter((item: VidispineItem | undefined) => item !== undefined),
        };
      } else {
        console.error("Response had no `item` field", response.data);
        return Promise.reject("vidispine returned invalid data");
      }
    case 400:
      console.log("vidispine returned bad request: ", response.data);
      return Promise.reject(
        "of an internal error: vidispine search was incorrect"
      );
    case 500 | 502 | 503 | 504:
      console.log(
        "vidispine server error ",
        response.status,
        ": ",
        response.data
      );
      return Promise.reject("vidispine is not responding correctly");
    default:
      console.log("vidispine returned ", response.status, ": ", response.data);
      return Promise.reject("vidispine returned an unexpected error");
  }
}

export { assetsForProject };
