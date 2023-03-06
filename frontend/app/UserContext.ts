import React from "react";

interface UserContextData {
  userName: string;
  isAdmin: boolean;
}

const UserContext = React.createContext<UserContextData | null>(null);
export const CoreUserContextProvider = UserContext.Provider;
export default UserContext;
