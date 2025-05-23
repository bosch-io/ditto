/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/* eslint-disable quotes */
import autoComplete from '@tarekraafat/autocomplete.js';
import * as ace from 'ace-builds/src-noconflict/ace';
import { Modal, Toast } from 'bootstrap';
import DOMPurify from 'dompurify';


const dom = {
  modalBodyConfirm: null,
  buttonConfirmed: null,
  toastContainer: null,
};

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export function ready() {
  getAllElementsById(dom);
}

/**
 * Adds a table to a table element
 * @param {HTMLTableElement} table tbody element the row is added to
 * @param {string} key first column text of the row. Acts as id of the row
 * @param {boolean} selected if true, the new row will be marked as selected
 * @param {boolean} clipBoardValue add a clipboard button at the last column of the row
 * @param {array} columnValues texts for additional columns of the row
 * @return {HTMLTableRowElement} created row
 */
export const addTableRow = function(table: HTMLTableElement, key: string, selected: boolean, clipBoardValue?: string, ...columnValues: string[]) {
  const row: HTMLTableRowElement = table.insertRow();
  row.id = key;
  addCellToRow(row, key, key, 0);
  let lastAddedColumn = key;
  columnValues.forEach((value) => {
    addCellToRow(row, value);
    lastAddedColumn = value;
  });
  if (selected) {
    row.classList.add('table-active');
  }
  if (clipBoardValue) {
    addActionToRow(row, ICON_CLASS_CLIPBOARD, getRowClipboardAction(ICON_CLASS_CLIPBOARD, ICON_CLASS_CLIPFEEDBACK, clipBoardValue), 'Copy to clipboard');
  }
  return row;
};

/**
 * Adds a checkbox as a firs column of a row
 * @param {HTMLTableRowElement} row target row
 * @param {string} id an id for the checkbox element
 * @param {boolean} checked check the checkbox
 * @param {boolean} disabled callback for the onchange event of the checkbox
 * @param {function} onToggle callback for the onchange event of the checkbox
 */
export function addCheckboxToRow(row: HTMLTableRowElement, id: string, checked: boolean, disabled: boolean = false, onToggle = null): HTMLInputElement {
  const td = row.insertCell(0);
  td.style.verticalAlign = 'middle';
  td.style.width = '25px';
  const checkBox = document.createElement('input');
  checkBox.classList.add('form-check-input');
  checkBox.type = 'checkbox';
  checkBox.id = id;
  checkBox.checked = checked;
  checkBox.disabled = disabled;
  onToggle && (checkBox.onchange = onToggle);
  td.append(checkBox);
  return checkBox;
}

/**
 * Adds a cell to the row including a tooltip
 * @param {HTMLTableRowElement} row target row
 * @param {string} cellContent content of new cell
 * @param {string} cellTooltip tooltip for new cell
 * @param {Number} position optional, default -1 (add to the end)
 * @return {HTMLElement} created cell element
 */
export function addCellToRow(row, cellContent, cellTooltip: string = null, position = -1) {
  const cell = row.insertCell(position);
  cell.textContent = cellContent;
  cell.setAttribute('data-bs-toggle', 'tooltip');
  cell.title = cellTooltip ?? cellContent;
  return cell;
}

/**
 * Adds a clipboard copy button to a row. The text of the previous table cell will be copied
 * @param {HTMLTableRowElement} row target row
 */
export function addActionToRow(row: HTMLTableRowElement, iconClass: string, onClickAction: (evt: Event) => any, toolTip?: string) {
  const td = row.insertCell();
  td.classList.add('table-action-column');
  const button = document.createElement('button');
  button.classList.add('btn');
  button.style.padding = '0';
  button.innerHTML = `<i class="bi ${iconClass}"></i>`;
  toolTip && (button.title = toolTip);
  button.onclick = onClickAction;
  td.appendChild(button);
}

export let ICON_CLASS_CLIPBOARD = 'bi-copy';
export let ICON_CLASS_CLIPFEEDBACK = 'bi-check-lg';
export function getRowClipboardAction(iconClassMain: string, iconClassFeedback: string, context: any) {
  return (evt: Event) => {
    navigator.clipboard.writeText(context);
    const icon = (evt.currentTarget as HTMLElement).querySelector('.bi');
    icon.classList.replace(iconClassMain, iconClassFeedback);
    setTimeout(() => icon.classList.replace(iconClassFeedback, iconClassMain), 500);
  };
}

/**
 * Adds a header cell to the given table row
 * @param {HTMLTableRowElement} row target row
 * @param {string} label label for the header cell
 */
export function insertHeaderCell(row, label: string) {
  const th = document.createElement('th');
  th.textContent = label;
  row.appendChild(th);
}

/**
 * Create a list of option elements for select element
 * @param {HTMLSelectElement} target target element (select)
 * @param {array} options Array of strings to be filled as options
 */
export function setOptions(target: HTMLSelectElement, options: string[]) {
  target.textContent = '';
  options.forEach((key) => {
    const option = document.createElement('option');
    option.text = key;
    target.appendChild(option);
  });
}

/**
 * Create a list of option elements for select element
 * @param {HTMLSelectElement} target target element (select)
 * @param {array} options Array of strings to be filled as options
 */
export function setOptionsWithText(target: HTMLSelectElement, options: any[]) {
  target.textContent = '';
  options.forEach((o) => {
    const option = document.createElement('option');
    option.value = o.key;
    option.text = o.text;
    target.appendChild(option);
  });
}

export function addDropDownEntries(target: HTMLUListElement, labels: Array<string>) {
  labels.forEach((label) => {
    addDropDownEntry(target, label);
  })
}

/**
 * Add a drop down items or header to Bootstrap dropdown
 * @param {HTMLElement} target target element
 * @param {string} label label of items for the drop down
 * @param {string} value (optional) additional data tag for the drop down item
 * @param {boolean} isHeader (optional) true to add a header line
 */
export function addDropDownEntry(target: HTMLUListElement, label: string, isHeader: boolean = false, value?: string) {
  const li = document.createElement('li');
  li.innerHTML = isHeader ?
    `<h6 class="dropdown-header" data-value='${value}'>${sanitizeHTML(label)}</h6>` :
    `<a class="dropdown-item" data-value='${value}'>${sanitizeHTML(label)}</a>`;
  target.appendChild(li);
}

/**
 * Add a tab pane in the UI. tab header and tab contens are added
 * @param {HTMLElement} tabItemsNode root node for the tabs
 * @param {HTMLElement} tabContentsNode root node for the tab contents
 * @param {string} title name of the new tab
 * @param {String} contentHTML tab content for the new tab
 * @param {string} toolTip (optional) toolip on the tab item
 */
export function addTab(tabItemsNode, tabContentsNode, title: string, contentHTML, toolTip: string = null) {
  const id = 'tab' + Math.random().toString(36).replace('0.', '');

  const li = document.createElement('li');
  li.classList.add('nav-item');
  if (toolTip) {
    li.setAttribute('data-bs-toggle', 'tooltip');
    li.setAttribute('title', toolTip);
  }
  li.innerHTML = `<a class="nav-link" data-bs-toggle="tab" data-bs-target="#${id}">${title}</a>`;
  tabItemsNode.appendChild(li);

  const template = document.createElement('template');
  template.innerHTML = contentHTML;
  template.content.firstElementChild.id = id;
  tabContentsNode.appendChild(template.content.firstElementChild);

  return id;
}

/**
 * Get the HTMLElements of all the given ids. The HTMLElements will be returned in the original object
 * @param {Object} domObjects object with empty keys that are used as ids of the dom elements
 * @param {DocumentFragment} searchRoot optional root to search in (optional, used for shadow dom)
 */
export function getAllElementsById(domObjects: object, searchRoot: DocumentFragment = null) {
  Object.keys(domObjects).forEach((id) => {
    domObjects[id] = (searchRoot ?? document).getElementById(id);
    if (!domObjects[id]) {
      throw new Error(`Element ${id} not found.`);
    }
  });
}

/**
 * Sanitizes the passed unsafeString to be safely used e.g. inside innerHTML without risking XSS.

 * @param unsafeString the string to sanitize.
 */
export function sanitizeHTML(unsafeString: string) {
  return DOMPurify.sanitize(unsafeString);
}

/**
 * Show an info toast
 * @param {string} message Message for toast
 * @param {string} header Header for toast
 */
export function showInfoToast(message: string, header: string = 'Info') {
  const domToast = document.createElement('div');
  domToast.classList.add('toast');
  domToast.innerHTML = `<div class="toast-header toast-header-info">
  <i class="bi me-2 bi-info-circle-fill"></i>
  <strong class="me-auto">${sanitizeHTML(header)}</strong>
  <small>${status}</small>
  <button type="button" class="btn-close" data-bs-dismiss="toast" aria-label="Close"></button>
  </div>
  <div class="toast-body">${sanitizeHTML(message)}</div>`;

  dom.toastContainer.appendChild(domToast);
  domToast.addEventListener("hidden.bs.toast", () => {
    domToast.remove();
  });
  const bsToast = new Toast(domToast);
  bsToast.show();
}

/**
 * Show an error toast
 * @param {string} message Message for toast
 * @param {string} header Header for toast
 * @param {string} status Status text for toas
 */
export function showError(message: string, header: string = 'Error', status: string = '') {
  const domToast = document.createElement('div');
  domToast.classList.add('toast');
  domToast.innerHTML = `<div class="toast-header toast-header-warn alert-danger">
  <i class="bi me-2 bi-exclamation-triangle-fill"></i>
  <strong class="me-auto">${sanitizeHTML(header)}</strong>
  <small>${status}</small>
  <button type="button" class="btn-close" data-bs-dismiss="toast" aria-label="Close"></button>
  </div>
  <div class="toast-body">${sanitizeHTML(message)}</div>`;

  dom.toastContainer.appendChild(domToast);
  domToast.addEventListener("hidden.bs.toast", () => {
    domToast.remove();
  });
  const bsToast = new Toast(domToast);
  bsToast.show();
}

/**
 * Error object for user errors
 * @param {string} message Message that was displayed to the user in an error toast
 */
function UserException(message: string) {
  this.message = message;
  this.name = 'UserException';
}

/**
 * User assertion. If the condition is false, a message is displayed to the user and execution breaks by throwing
 * an error.
 * @param {boolean} condition If false, an error is shown to the user
 * @param {string} message Message to be shown to the user
 * @param {HTMLElement} validatedElement Optional element that was validated
 */
export function assert(condition, message: string, validatedElement = null) {
  if (validatedElement) {
    validatedElement.classList.remove('is-invalid');
  }
  if (!condition) {
    if (validatedElement) {
      validatedElement.parentNode.getElementsByClassName('invalid-feedback')[0].textContent = message;
      validatedElement.classList.add('is-invalid');
    } else {
      showError(message, 'Error');
    }
    throw new UserException(message);
  }
}

/**
 * Simple Date format that makes ISO string more readable and cuts off the milliseconds
 * @param {string} dateISOString to format
 * @param {boolean} withMilliseconds don t cut off milliseconds if true
 * @return {string} formatted date
 */
export function formatDate(dateISOString: string, withMilliseconds = false): string {
  if (withMilliseconds) {
    return dateISOString.replace('T', ' ').replace('Z', '').replace('.', ' ');
  } else {
    return dateISOString.split('.')[0].replace('T', ' ');
  }
}

let modalConfirm;

/**
 * Like from bootbox or bootprompt
 * @param {string} message confirm message
 * @param {string} action button text
 * @param {function} callback true if confirmed
 */
export function confirm(message: string, action: string, callback) {
  modalConfirm = modalConfirm ?? new Modal('#modalConfirm');
  dom.modalBodyConfirm.textContent = message;
  dom.buttonConfirmed.innerText = action;
  dom.buttonConfirmed.onclick = callback;
  modalConfirm.show();
}

/**
 * Creates and configures an ace editor
 * @param {string} domId id of the dom element for the ace editor
 * @param {*} sessionMode session mode of the ace editor
 * @param {*} readOnly sets the editor to read only and removes the line numbers
 * @param {*} wrap sets the editor wrap option.
 * @return {*} created ace editor
 */
export function createAceEditor(domId: string, sessionMode, readOnly = false, wrap = false) {
  const result = ace.edit(domId);
  result.setOption('wrap', wrap);
  result.setOption('useWorker', false);
  result.session.setMode(sessionMode);
  if (readOnly) {
    result.setReadOnly(true);
    result.renderer.setShowGutter(false);
  }

  return result;
}

/**
 * Creates a autocomplete input field
 * @param {string} selector selector for the input field
 * @param {function} src src
 * @param {string} placeHolder placeholder for input field
 * @return {Object} autocomplete instance
 */
export function createAutoComplete(selector: string, src, placeHolder: string) {
  // eslint-disable-next-line new-cap
  return new autoComplete({
    selector: selector,
    data: {
      src: src,
      keys: ['label', 'group'],
    },
    placeHolder: placeHolder,
    resultsList: {
      class: 'dropdown-menu show',
      maxResults: 30,
    },
    resultItem: {
      class: 'dropdown-item',
      highlight: true,
      element: (item, data) => {
        item.style = 'display: flex;';
        item.innerHTML = `<span style="flex-grow: 1;" >${sanitizeHTML(data.key === 'label' ? data.match : data.value.label)}</span>
            <span style="color: 3a8c9a;" class="fw-light fst-italic ms-1">
              ${data.key === 'group' ? data.match : data.value.group}</span>`;
      },
    },
    events: {
      input: {
        results: () => {
          Array.from(document.getElementsByClassName('resizable_pane')).forEach((resizePane: HTMLElement) => {
            resizePane.style.overflow = 'unset';
          });
        },
        close: () => {
          Array.from(document.getElementsByClassName('resizable_pane')).forEach((resizePane: HTMLElement) => {
            resizePane.style.overflow = 'auto';
          });
        },
      },
    },
  });
}

/**
 * Links the hidden input element for validation to the table
 * @param {HTMLElement} tableElement tbody that is validated
 * @param {HTMLElement} inputElement input element with validation
 */
export function addValidatorToTable(tableElement, inputElement) {
  tableElement.addEventListener('click', () => {
    inputElement.classList.remove('is-invalid');
  });
}

/**
 * Adjust selection of a table
 * @param {HTMLTableElement} tbody table with the data
 * @param {function} condition evaluate if table row should be selected or not
 */
export function tableAdjustSelection(tbody: HTMLTableElement, condition: (row: HTMLTableRowElement) => boolean) {
  Array.from(tbody.rows).forEach((row) => {
    if (condition(row)) {
      row.classList.add('table-active');
    } else {
      row.classList.remove('table-active');
    }
  });
}

/**
 * JSON.stringify object, using indentation of 2
 * @param {Object} jsonObject to stringify
 * @return {string} JSON formatted string
 */
export function stringifyPretty(jsonObject: Object): string {
  return JSON.stringify(jsonObject, null, 2);
}

/**
 * searches the placeholder in the input field and marks it to be replaced by the user
 * @param input dom input element
 * @param placeholder text to search and mark
 * @returns 
 */
export function checkAndMarkInInput(input: HTMLInputElement, placeholder: string) {
  const index = input.value.indexOf(placeholder);
  if (index >= 0) {
    input.focus();
    input.setSelectionRange(index, index + placeholder.length);
    return true;
  } else {
    return false;
  }
}

/**
 * Create the counter text for two lists.
 * if the list is empty, no counter is shown
 * if there are two lists given of different lenght, "x/y" is shown
 * @param badgeElement dom element for the badge
 * @param list list to count elements
 * @param partialList optional partial list to display x of y as "x/y"
 */
export function updateCounterBadge(badgeElement: HTMLSpanElement, list: Array<any>, partialList?: Array<any>) {
  if (!partialList || partialList.length === list.length) {
    badgeElement.textContent = list.length > 0 ? list.length.toString() : '';
  } else {
    badgeElement.textContent = `${partialList.length}/${list.length}`;
  }
}



