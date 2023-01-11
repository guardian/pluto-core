import React, { useEffect, useState } from "react";
import { useGuardianStyles } from "~/misc/utils";
import { getItemsNotDeleted } from "~/ProjectEntryList/helpers";

interface NotDeletedProps {
  projectId: number;
}

const NotDeleted: React.FC<NotDeletedProps> = (props) => {
  const [itemsNotDeleted, setItemsNotDeleted] = useState<ItemsNotDeleted[]>([]);

  const classes = useGuardianStyles();

  const getDeleteItemData = async () => {
    try {
      const returnedItems = await getItemsNotDeleted(props.projectId);
      setItemsNotDeleted(returnedItems);
    } catch {
      console.log("Could not load items that where not deleted.");
    }
  };

  useEffect(() => {
    getDeleteItemData();
  }, []);

  return (
    <>
      {itemsNotDeleted.length > 0 ? (
        <>
          <br />
          <br />
          No attempt to delete the following items was made due to them being in
          more than one project:-
          <br />
        </>
      ) : null}
      {itemsNotDeleted
        ? itemsNotDeleted.map((vidispine_item, index) => {
            const { id, projectEntry, item } = vidispine_item;
            return (
              <div key={index}>
                <a href={"/vs/item/" + item} target="_blank">
                  {item}
                </a>
                <br />
              </div>
            );
          })
        : null}
    </>
  );
};

export default NotDeleted;
