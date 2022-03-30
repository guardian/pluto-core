//this is needed to prevent TypeError: document.createRange is not a function when testing UsersAutoComplete
//See https://stackoverflow.com/questions/60333156/how-to-fix-typeerror-document-createrange-is-not-a-function-error-while-testi
import PopperJs from "popper.js";

export default class Popper {
  constructor() {
    this.placements = PopperJs.placements;

    return {
      update: () => {},
      destroy: () => {},
      scheduleUpdate: () => {},
    };
  }
}
