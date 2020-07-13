import Axios from "axios";

const API = "/api";
const API_IS_LOGGED_IN = `${API}/isLoggedIn`;

export const isLoggedIn = async (): Promise<PlutoUser> => {
  try {
    const { status, data } = await Axios.get<PlutoUser>(`${API_IS_LOGGED_IN}`);

    if (status === 200) {
      return data;
    }

    throw new Error(`Could not retrieve who is logged in. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};
