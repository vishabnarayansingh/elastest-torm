import { LogAnalyzerService } from './log-analyzer.service';
import { AgGridColumn } from 'ag-grid-angular/main';
import { Router } from '@angular/router';
import { Observable } from 'rxjs/Rx';
import { RowSelectedEvent, RowDoubleClickedEvent, RowDataChangedEvent } from 'ag-grid-community/dist/lib/events';
import { LogAnalyzerModel } from './log-analyzer-model';
import { GetIndexModalComponent } from '../elastest-log-analyzer/get-index-modal/get-index-modal.component';
import { AfterViewInit, Component, ElementRef, OnInit, ViewChild, Input, OnDestroy } from '@angular/core';
import { dateToInputLiteral, invertColor } from './utils/Utils';
import { MatDialog, MatDialogRef } from '@angular/material';
import {
  ColumnApi,
  ComponentStateChangedEvent,
  GridApi,
  GridOptions,
  GridReadyEvent,
  RowNode,
  Column,
} from 'ag-grid-community/main';

import { TreeComponent } from 'angular-tree-component';
import { ShowMessageModalComponent } from './show-message-modal/show-message-modal.component';
import { LogAnalyzerConfigModel } from './log-analyzer-config-model';
import { MarkComponent } from './mark-component/mark.component';
import { TJobExecService } from '../elastest-etm/tjob-exec/tjobExec.service';
import { TJobExecModel } from '../elastest-etm/tjob-exec/tjobExec-model';
import { TitlesService } from '../shared/services/titles.service';
import { ExternalService } from '../elastest-etm/external/external.service';
import { ExternalTJobExecModel } from '../elastest-etm/external/external-tjob-execution/external-tjob-execution-model';
import { MonitoringService } from '../shared/services/monitoring.service';
import { MonitoringQueryModel } from '../shared/monitoring-query.model';
import { LogAnalyzerQueryModel } from '../shared/loganalyzer-query.model';
import { TreeCheckElementModel } from '../shared/ag-tree-model';
import { interval } from 'rxjs';
import { ColumnState } from 'ag-grid-community/dist/lib/columnController/columnController';

@Component({
  selector: 'elastest-log-analyzer',
  templateUrl: './elastest-log-analyzer.component.html',
  styleUrls: ['./elastest-log-analyzer.component.scss'],
})
export class ElastestLogAnalyzerComponent implements OnInit, AfterViewInit, OnDestroy {
  private charsByLine: number = 0;

  public gridApi: GridApi;
  public gridColumnApi: ColumnApi;

  public logAnalyzerQueryModel: LogAnalyzerQueryModel;
  public logAnalyzer: LogAnalyzerModel;

  public logRows: any[] = [];
  public logColumns: any[] = [];
  public autoRowHeight: boolean = true;

  public overlayLoadingTemplate: string = '<span class="ag-overlay-loading-center">Please wait while logs are loading</span>';

  public gridOptions: GridOptions = {
    defaultColDef: {
      resizable: true,
    },
    suppressSetColumnStateEvents: true,
    rowHeight: 22,
    headerHeight: 42,
    rowSelection: 'single',
    suppressRowClickSelection: false,
    suppressCellSelection: false, // Only supress key navigation and focus
    suppressChangeDetection: true,
    rowModelType: 'clientSide',
    suppressDragLeaveHidesColumns: true,
    enableCellChangeFlash: true,
    getRowStyle: this.setRowsStyle,
    /*getRowHeight: function(params) {
      return 18 * (Math.floor(params.data.message.length / 30) + 1);
    },*/
    overlayLoadingTemplate: this.overlayLoadingTemplate,
  };

  @Input()
  tJobId: number;
  @Input()
  tJobExecId: number;
  @Input()
  testCase: string;
  @Input()
  isEmbed: boolean = false;
  @Input()
  componentStreams: any;
  @Input()
  exTJob: number;
  @Input()
  exTJobExec: number;
  @Input()
  exTestCase: number;
  @Input()
  exTestExec: number;

  @ViewChild('fromDate')
  fromDate: ElementRef;
  @ViewChild('toDate')
  toDate: ElementRef;
  @ViewChild('mark')
  mark: MarkComponent;

  // Buttons
  public showLoadMore: boolean = false;
  public disableLoadMore: boolean = false;
  public showPauseTail: boolean = false;

  disableBtns: boolean = false;

  // Filters
  @ViewChild('componentsTree')
  componentsTree: TreeComponent;
  @ViewChild('levelsTree')
  levelsTree: TreeComponent;

  // TestCase
  withTestCase: boolean = false;
  testCaseName: string = undefined;

  monitoringService: MonitoringService;

  constructor(
    public dialog: MatDialog,
    public router: Router,
    private logAnalyzerService: LogAnalyzerService,
    private tJobExecService: TJobExecService,
    private titlesService: TitlesService,
    private externalService: ExternalService,
  ) {
    this.monitoringService = this.logAnalyzerService.monitoringService;
  }

  ngOnInit(): void {
    if (!this.isEmbed) {
      this.titlesService.setPathName(this.router.routerState.snapshot.url);
    }
    this.logAnalyzer = new LogAnalyzerModel();
    this.initLogAnalyzer();
    this.logAnalyzerService.getLogAnalyzerConfig().subscribe(
      (logAnalyzerConfig: LogAnalyzerConfigModel) => {
        if (logAnalyzerConfig !== undefined) {
          this.logAnalyzer.laConfig = logAnalyzerConfig;
        }
      },
      (error: Error) => {
        // Do nothing
      },
    );
    this.initLogAnalyzerQueryModel();
  }

  ngAfterViewInit(): void {
    this.fromDate.nativeElement.value = this.getDefaultFromValue();
    this.toDate.nativeElement.value = this.getDefaultToValue();
  }

  ngOnDestroy(): void {
    this.logAnalyzer.stopTail();
  }

  public getRowsData(): any[] {
    if (this.gridApi) {
      let rowData: any[] = [];
      this.gridApi.forEachNode((node: RowNode) => rowData.push(node.data));
      return rowData;
    } else {
      return this.logRows;
    }
  }

  /***** INIT *****/

  initLogAnalyzer(): void {
    let params: any = this.router.parseUrl(this.router.url).queryParams;
    let fromExec: any;
    if ((params.tjob && params.exec) || (this.tJobId !== undefined && this.tJobExecId !== undefined)) {
      fromExec = {
        type: 'normal',
        tJob: params.tjob ? params.tjob : this.tJobId ? this.tJobId : undefined,
        exec: params.exec ? params.exec : this.tJobExecId ? this.tJobExecId : undefined,
        testCase: params.testCase ? params.testCase : this.testCase ? this.testCase : undefined,
      };
    } else if ((params.exTJob && params.exTJobExec) || (this.exTJob !== undefined && this.exTJobExec !== undefined)) {
      fromExec = {
        type: 'external',
        exTJob: params.exTJob ? params.exTJob : this.exTJob ? this.exTJob : undefined,
        exTJobExec: params.exTJobExec ? params.exTJobExec : this.exTJobExec ? this.exTJobExec : undefined,
        exTestCase: params.exTestCase ? params.exTestCase : this.exTestCase ? this.exTestCase : undefined,
        exTestExec: params.exTestExec ? params.exTestExec : this.exTestExec ? this.exTestExec : undefined,
      };
    }
    if (!this.isEmbed) {
      // Timeout is a hack to remove console ExpressionChangedAfterItHasBeenCheckedError error
      setTimeout(() => this.openSelectExecutions(fromExec), 200);
    } else {
      if (this.tJobId && this.tJobExecId) {
        this.tJobExecService.getTJobExecutionByTJobId(this.tJobId, this.tJobExecId).subscribe(
          (tjobExec: TJobExecModel) => {
            let data: { fromDate: Date; selectedIndices: string[]; toDate: Date } = {
              fromDate: tjobExec.startDate,
              selectedIndices: tjobExec.getSplittedComposedMonitoringIndex(),
              toDate: tjobExec.endDate,
            };
            this.loadSelectExecutions(data, fromExec);
          },
          (error: Error) => console.log(error),
        );
      } else if (this.exTJob && this.exTJobExec) {
        this.externalService.getExternalTJobExecById(this.exTJobExec).subscribe((exTJobExec: ExternalTJobExecModel) => {
          let data: { fromDate: Date; selectedIndices: string[]; toDate: Date } = {
            fromDate: exTJobExec.startDate,
            selectedIndices: exTJobExec.getSplittedComposedMonitoringIndex(),
            toDate: exTJobExec.endDate,
          };
          this.loadSelectExecutions(data, fromExec);
        });
      }
    }
  }
  // Function to init some parameters of ag-grid
  onGridReady(params: GridReadyEvent | ComponentStateChangedEvent): void {
    this.gridApi = params.api;
    this.gridColumnApi = params.columnApi;

    if (this.logAnalyzer.laConfig.columnsState) {
      this.loadColumnsConfig(false);
    } else {
      this.gridApi.sizeColumnsToFit(); // State is saved automatically
    }
    this.toggleRowHeight();
  }

  initLogAnalyzerQueryModel(): void {
    this.logAnalyzerQueryModel = new LogAnalyzerQueryModel();
  }

  /**********************/
  /***** Load Utils *****/
  /**********************/

  popup(msg: string, buttonMsg: string = 'OK'): void {
    let popupDuration: number = this.logAnalyzer.usingTail ? 1 : undefined;
    let popupCss: any[] = this.logAnalyzer.usingTail ? ['snackBarHidden'] : [];

    this.monitoringService.popupService.openSnackBar(msg, buttonMsg, popupDuration, popupCss);
  }

  prepareLoadLog(): void {
    this.prepareLoadLogBasic();
    this.setTerms();
    this.setMatch();
  }
  prepareLoadLogBasic(): void {
    this.disableBtns = true;
    this.initLogAnalyzerQueryModel();

    this.logAnalyzer.fromDate = this.getFromDate();
    this.logAnalyzer.toDate = this.getToDate();

    this.logAnalyzerQueryModel.indices = this.logAnalyzer.selectedIndices;
    this.logAnalyzerQueryModel.filterPathList = this.logAnalyzerService.filters;
    this.logAnalyzerQueryModel.size = this.logAnalyzer.maxResults;

    this.setRange();
  }

  setRange(): void {
    this.setRangeByGiven();
  }

  setRangeByGiven(
    from: Date | string = this.getFromDate(),
    to: Date | string = this.getToDate(),
    includedFrom: boolean = true,
    includedTo: boolean = true,
  ): void {
    this.logAnalyzerQueryModel = this.logAnalyzerService.setTimeRangeToLogAnalyzerQueryModel(
      this.logAnalyzerQueryModel,
      from,
      to,
      includedFrom,
      includedTo,
    );
  }

  setTerms(): void {
    let componentStreamsChecked: TreeCheckElementModel[] = this.logAnalyzer.componentsStreams.getOnlyCheckedTree();
    if (componentStreamsChecked.length > 0) {
      this.logAnalyzerQueryModel.componentsStreams = componentStreamsChecked;
    }

    if (!this.logAnalyzer.levels.empty()) {
      let levels: string[] = [];
      for (let level of this.logAnalyzer.levels.tree) {
        if (level.checked) {
          levels.push(level.name);
        }
      }
      this.logAnalyzerQueryModel.levels = levels;
    }

    // TODO fix levels for experimental (in mini works fine)
  }

  setMatch(msg: string = this.logAnalyzer.messageFilter): void {
    this.logAnalyzerQueryModel = this.logAnalyzerService.setMatchByGivenLogAnalyzerQueryModel(msg, this.logAnalyzerQueryModel);
  }

  setTableHeader(): void {
    this.logColumns = [];

    for (let field of this.logAnalyzerQueryModel.filterPathList) {
      if (field !== 'stream_type' && field !== 'et_type') {
        // stream_type is always log
        let columnObj: AgGridColumn = new AgGridColumn();
        columnObj.headerName = field;
        columnObj.field = field;
        columnObj.width = 240;
        columnObj.suppressSizeToFit = false;
        columnObj.tooltipField = field;

        switch (field) {
          case '@timestamp':
            columnObj.maxWidth = 232;
            columnObj.hide = this.isEmbed;
            break;
          case 'message':
            break;
          case 'level':
            columnObj.maxWidth = 100;
            columnObj.hide = this.isEmbed;
            break;
          case 'component':
            columnObj.maxWidth = 260;
            break;
          case 'stream':
            columnObj.maxWidth = 188;
            columnObj.hide = this.isEmbed;
            break;
          case 'exec':
            columnObj.maxWidth = 80;
            columnObj.hide = this.isEmbed;
            break;
          default:
            break;
        }

        this.logColumns.push(columnObj);

        if (this.gridApi) {
          this.gridApi.refreshHeader();
        }
      }
    }
  }

  updateButtons(show: boolean = true): void {
    this.showLoadMore = show;
    this.disableLoadMore = !show;
    this.showPauseTail = !show;
  }

  showLoadMsg(): void {
    if (this.gridOptions.api) {
      this.gridOptions.api.showLoadingOverlay();
    }
  }

  showNoLogsMsg(): void {
    if (this.gridOptions.api) {
      this.gridOptions.api.showNoRowsOverlay();
    }
  }

  hideOverlayMsg(): void {
    if (this.gridApi) {
      this.gridApi.hideOverlay();
    }
  }

  /**************************/
  /***** Load functions *****/
  /**************************/

  public updateData(data: any[], operation: 'assign' | 'concat'): void {
    if (data !== undefined && data !== null) {
      if (operation === 'assign') {
        this.logRows = data;
      } else if (operation === 'concat') {
        this.logRows = this.logRows.concat(data);
      }
    } else {
      this.logRows = [];
    }
    if (this.gridApi) {
      this.gridApi.setRowData(this.logRows);
    }
  }

  verifyAndLoadLog($event, loadLog: boolean): void {
    $event.preventDefault(); // On enter key pressed always opens modal. Prevent this
    if (loadLog) {
      this.loadLog();
    } else {
      this.popup('The search can not be performed. Please complete all required fields');
    }
  }

  // MAIN
  loadLog(withoutPrepare: boolean = false): void {
    if (this.withTestCase && this.testCaseName) {
      this.filterTestCase(this.testCaseName);
      return;
    }
    this.showLoadMsg();

    this.logAnalyzer.usingTail = this.logAnalyzer.tail;
    this.logAnalyzer.stopTail();

    this.logAnalyzer.selectedRow = undefined;

    if (!withoutPrepare) {
      this.prepareLoadLog();
    }

    this.monitoringService.searchLogAnalyzerQuery(this.logAnalyzerQueryModel).subscribe(
      (logs: any) => {
        this.loadLogByGivenData(logs);
      },
      (error: Error) => {
        this.disableBtns = false;
        this.hideOverlayMsg();
      },
    );
  }

  loadLogByGivenData(data: any[] = []): void {
    this.updateData(data, 'assign');

    let logsLoaded: boolean = this.getRowsData().length > 0;
    if (logsLoaded) {
      this.setTableHeader();
      this.mark.removeAllPatterns();
      if (!this.isEmbed) {
        this.popup('Logs has been loaded');
      }
    } else {
      if (!this.isEmbed) {
        this.popup("There aren't logs to load", 'OK');
      }
    }
    this.updateButtons(logsLoaded);
    if (this.logAnalyzer.usingTail) {
      this.loadTailLog(!logsLoaded);
    }
    this.disableBtns = false;
  }

  loadTailLog(notLoadedPrevious: boolean = false): void {
    let timer: Observable<number>;
    this.updateButtons(false);

    timer = interval(3500);
    this.logAnalyzer.tailSubscription = timer.subscribe(() => {
      if (!this.logAnalyzer.pauseTail) {
        this.loadMore(true);
      }
    });
  }

  // MAIN
  loadMore(fromTail: boolean = false): void {
    this.showLoadMsg();
    this.prepareLoadLog();
    let rows: any[] = this.getRowsData();
    let lastTrace: any = rows[rows.length - 1];
    this.logAnalyzerQueryModel.searchAfterTrace = lastTrace;
    if (fromTail) {
      this.setRangeByGiven(lastTrace['@timestamp'], 'now');
      this.logAnalyzerQueryModel.size = 100;
    }

    this.monitoringService.searchLogAnalyzerQuery(this.logAnalyzerQueryModel).subscribe(
      (data: any) => {
        let moreRows: any[] = data;
        if (moreRows.length > 0) {
          this.updateData(moreRows, 'concat');

          this.popup('Loaded more logs');
          this.setTableHeader();
          this.updateButtons(!fromTail);
          this.mark.searchByPatterns();
        } else {
          this.popup("There aren't more logs to load", 'OK');
          if (this.getRowsData().length > 0) {
            // Only hide overlay if there are data
            this.hideOverlayMsg();
          }
          this.disableLoadMore = true; // removed from html temporally
        }
        this.disableBtns = false;
      },
      (error: Error) => {
        this.disableBtns = false;
        this.hideOverlayMsg();
      },
    );
  }

  // MAIN
  moreFromSelected(): void {
    if (this.logAnalyzer.hasSelectedRow()) {
      this.showLoadMsg();
      let selected: number = this.logAnalyzer.selectedRow;
      let rows: any[] = this.getRowsData();
      if (selected === rows.length - 1) {
        // If selected is last
        this.loadMore();
      } else {
        this.prepareLoadLog();
        let from: Date = rows[selected]['@timestamp'];
        let to: Date = rows[selected + 1]['@timestamp'];
        this.setRangeByGiven(from, to, true, false);

        this.logAnalyzerQueryModel.searchAfterTrace = rows[selected];
        this.logAnalyzerQueryModel.searchBeforeTrace = rows[selected + 1];
        this.monitoringService.searchLogAnalyzerQuery(this.logAnalyzerQueryModel).subscribe(
          (moreRows: any[]) => {
            if (moreRows.length > 0) {
              this.insertRowsFromPosition(selected, moreRows);

              this.popup('Loaded more logs from selected trace');
              this.setTableHeader();
              this.updateButtons();
              this.mark.searchByPatterns();
            } else {
              this.popup("There aren't logs to load or you don't change filters", 'OK');
              if (this.getRowsData().length > 0) {
                // Only hide overlay if there are data
                this.hideOverlayMsg();
              }
            }
            this.disableBtns = false;
          },
          (error: Error) => {
            this.disableBtns = false;
            this.hideOverlayMsg();
          },
        );
      }
    } else {
      this.popup("There isn't trace selected. Please, do click on a row", 'OK');
    }
  }

  insertRowsFromPosition(pos: number, newRows: any[]): void {
    let rows: any[] = this.getRowsData();

    let firstHalf: any[] = rows.slice(0, pos + 1);
    let secondHalf: any[] = rows.slice(pos + 1);

    this.updateData(firstHalf.concat(newRows).concat(secondHalf), 'assign');
  }

  /***************************/
  /***** Grid and Events *****/
  /***************************/

  public rowDataChanged($event: RowDataChangedEvent): void {
    if (this.getRowsData().length > 0) {
      // Only hide overlay if there are data
      this.hideOverlayMsg();
    }
  }

  public componentStateChanged($event: ComponentStateChangedEvent): void {
    // On changes detected
    this.onGridReady($event);
    this.mark.model = this;
    if (this.logAnalyzer.usingTail) {
      $event.api.ensureIndexVisible(this.getRowsData().length - 1, 'undefined');
    }
  }

  public setRowsStyle(params: any): any {
    let style: any;
    let init: boolean = false;
    if (params.data.marked) {
      if (!init) {
        init = true;
        style = {};
      }
      style.background = params.data.marked;
      style.color = invertColor(params.data.marked, true);
    }
    if (params.data.focused) {
      if (!init) {
        init = true;
        style = {};
      }
      style.background = '#666666';
      style.color = invertColor(style.background, true);
    }
    return style;
  }

  public switchRowSelection($event: RowSelectedEvent): void {
    let row: RowNode = $event.node;
    if (row.isSelected()) {
      this.logAnalyzer.selectedRow = $event.rowIndex;
    } else {
      if (this.logAnalyzer.selectedRow === $event.rowIndex) {
        this.logAnalyzer.selectedRow = undefined;
      }
    }
  }
  public openMessageModal($event: RowDoubleClickedEvent): void {
    let dialogRef: MatDialogRef<ShowMessageModalComponent> = this.dialog.open(ShowMessageModalComponent, {
      data: { row: $event.data, columns: this.logColumns },
      height: '90%',
      width: '80%',
    });
  }

  public redrawRows(): void {
    if (this.gridApi) {
      this.gridApi.redrawRows();
    }
  }

  toggleRowHeight(checked: boolean = this.autoRowHeight): void {
    this.autoRowHeight = checked;
    if (this.autoRowHeight) {
      let messageColumn: any = this.gridOptions.columnApi.getAllDisplayedColumns().filter((column: Column) => {
        // message_1 temporally too
        return column.getColId() === 'message' || column.getColId() === 'message_1';
      })[0];
      if (messageColumn && messageColumn.actualWidth) {
        this.setRowHeight(messageColumn.actualWidth);
      }
    } else {
      this.gridOptions.api.resetRowHeights();
    }
  }

  public setRowHeight(columnWidth: number): void {
    let fontSize: number = 13;
    // Left+right
    let padding: number = 12;
    // Left+right
    let border: number = 2;
    this.charsByLine = Math.trunc((columnWidth - padding - border) / 7.85);
    this.gridOptions.api.forEachNode((rowNode: RowNode) => {
      let singleLineHeight: number = 20;

      let lineBreaksMatches: any = rowNode.data.message.match(/\n/g);
      let lineBreaks: number = 0;
      if (lineBreaksMatches && lineBreaksMatches.length > 0) {
        lineBreaks = lineBreaksMatches.length;
      }

      let height: number = 0;
      if (lineBreaks > 0) {
        let splittedMsg: string[] = rowNode.data.message.split('\n');

        for (let msg of splittedMsg) {
          height += singleLineHeight * Math.ceil(msg.length / this.charsByLine);
        }
      } else {
        // Normal
        height = singleLineHeight * Math.ceil(rowNode.data.message.length / this.charsByLine);
      }

      height < singleLineHeight ? (height = singleLineHeight) : (height = height);
      rowNode.setRowHeight(height);
    });
    this.gridOptions.api.onRowHeightChanged();
  }

  public saveColumnsConfig(event: any, showPopup: boolean = true, persist: boolean = false): void {
    if (this.autoRowHeight && event && event.column && event.column.colId === 'message' && event.finished) {
      this.setRowHeight(event.column.actualWidth);
    }
    this.logAnalyzer.laConfig.columnsState = this.gridColumnApi.getColumnState();
    if (persist) {
      this.logAnalyzerService.saveLogAnalyzerConfig(this.logAnalyzer.laConfig).subscribe(
        (logAnalyzerConfig: LogAnalyzerConfigModel) => {
          this.logAnalyzer.laConfig = logAnalyzerConfig;
        },
        (error: Error) => {
          this.popup('An error occurred while trying to save the configuration');
          console.log('Error on save LogAnalyzer column configuration:', error);
        },
      );
    }
    if (showPopup) {
      this.popup('Columns configuration has been saved');
    }
  }

  public loadColumnsConfig(showPopup: boolean = true): void {
    if (this.logAnalyzer.laConfig.columnsState) {
      // TMP fix for https://github.com/ag-grid/ag-grid/issues/2889
      let columnsStates: ColumnState[] = this.logAnalyzer.laConfig.columnsState;
      if (
        this.gridColumnApi.getPrimaryColumns().length > 0 &&
        this.gridColumnApi.getPrimaryColumns()[0].getColId() &&
        this.gridColumnApi
          .getPrimaryColumns()[0]
          .getColId()
          .endsWith('_1')
      ) {
        columnsStates = [];
        for (let columnState of this.logAnalyzer.laConfig.columnsState) {
          // Clone
          let newColumnState: ColumnState = Object.assign({}, columnState);

          newColumnState.colId = newColumnState.colId + '_1';
          columnsStates.push(newColumnState);
        }
      } else if (
        this.logAnalyzer.laConfig.columnsState.length > 0 &&
        this.logAnalyzer.laConfig.columnsState[0].colId &&
        this.logAnalyzer.laConfig.columnsState[0].colId.endsWith('_1')
      ) {
        columnsStates = [];
        for (let columnState of this.logAnalyzer.laConfig.columnsState) {
          // Clone
          let newColumnState: ColumnState = Object.assign({}, columnState);

          newColumnState.colId = newColumnState.colId.split('_1')[0];
          columnsStates.push(newColumnState);
        }
      }

      this.gridColumnApi.setColumnState(columnsStates);
      if (showPopup) {
        this.popup('Saved columns configuration has been loaded');
      }
    }
  }

  public loadSavedColumnsConfig(): void {
    this.logAnalyzerService.getLogAnalyzerConfig().subscribe(
      (logAnalyzerConfig: LogAnalyzerConfigModel) => {
        if (logAnalyzerConfig !== undefined) {
          this.logAnalyzer.laConfig = logAnalyzerConfig;
          this.loadColumnsConfig(false);
        } else {
          this.popup('There is not any saved configuration to load');
        }
      },
      (error: Error) => {
        this.popup('Error on load saved configuration. Possibly there is not saved config to load');
      },
    );
  }

  public loadDefaultColumnsConfig(): void {
    this.logAnalyzer.laConfig = new LogAnalyzerConfigModel();
    this.setTableHeader();
    this.popup('Default columns configuration has been loaded');
  }

  /**********************/
  /***** Date utils *****/
  /**********************/

  public getDefaultFromValue(): string {
    return dateToInputLiteral(this.logAnalyzer.getDefaultFromDate());
  }

  public getDefaultToValue(): string {
    return dateToInputLiteral(this.logAnalyzer.getDefaultToDate());
  }

  public getFromDate(): any {
    return this.fromDate.nativeElement.value;
  }

  public getToDate(): any {
    return this.toDate.nativeElement.value;
  }

  // public getFromDate(): Date {
  //   let timezoneOffset: number = new Date().getTimezoneOffset();
  //   let date: Date = new Date(this.getFromDateStr());
  //   return new Date(date.getTime() - timezoneOffset * 60000);
  // }

  // public getToDate(): Date {
  //   let timezoneOffset: number = new Date().getTimezoneOffset();
  //   let date: Date = new Date(this.getToDateStr());
  //   return new Date(date.getTime() - timezoneOffset * 60000);
  // }

  public setFromDate(date: Date): void {
    this.fromDate.nativeElement.value = dateToInputLiteral(date);
  }

  public setToDate(date: Date): void {
    this.toDate.nativeElement.value = dateToInputLiteral(date);
  }

  public setUseTail(tail: boolean): void {
    this.logAnalyzer.tail = tail;
  }

  public setWithTestCase(testCase: boolean): void {
    this.withTestCase = testCase;
  }

  /**** Modal ****/
  public openSelectExecutions(fromExec?: any): void {
    let dialogRef: MatDialogRef<GetIndexModalComponent> = this.dialog.open(GetIndexModalComponent, {
      data: fromExec,
      height: '90%',
      width: '80%',
    });
    dialogRef.afterClosed().subscribe((data: any) => {
      this.loadSelectExecutions(data, fromExec);
    });
  }

  loadSelectExecutions(data: any, fromExec: any): void {
    if (data) {
      // Ok Pressed
      if (data.selectedIndices.length > 0 && data.selectedIndices !== '') {
        this.logAnalyzer.selectedIndices = data.selectedIndices;
        if (data.fromDate) {
          this.setFromDate(data.fromDate);
        }
        if (data.toDate) {
          this.setToDate(data.toDate);
        }
        if (this.isEmbed && this.componentStreams) {
          this.logAnalyzer.setComponentsStreams(this.componentStreams);
          this.componentsTree.treeModel.update();
        } else {
          this.loadComponentStreams();
        }
        this.loadLevels();
        this.withTestCase = fromExec && (fromExec.testCase || fromExec.exTestCase);
        if (this.withTestCase) {
          if (fromExec.testCase) {
            this.testCaseName = fromExec.testCase;
          } else {
            this.testCaseName = fromExec.exTestCase;
          }
        }
        this.loadLog();
      } else {
        this.popup('No execution was selected. Selected all by default');
        this.loadLog();
      }
    } else {
    }
  }

  filterTestCase(testCase: string): void {
    this.showLoadMsg();
    let startMsg: string = this.logAnalyzerService.startTestCasePrefix + testCase;
    let endMsg: string = this.logAnalyzerService.endTestCasePrefix + testCase;

    // too old Date for search start test trace, to evit problems
    let startDateForStartMsg: Date = new Date(1900, 1);

    // Search Start Msg
    // TODO use refactorized method into logAnalyzer Service
    this.searchTraceByGivenMsg(startMsg, startDateForStartMsg).subscribe(
      (startData: any) => {
        if (startData.length > 0) {
          let startRow: any = startData[0];
          this.setFromDate(new Date(startRow['@timestamp']));
          // Search Finish Msg
          this.searchTraceByGivenMsg(endMsg).subscribe(
            (finishData: any) => {
              if (finishData.length > 0) {
                let finishRow: any = finishData[0];
                // Add 1 second to solve end trace milliseconds and future searches
                let toDate: Date = new Date(new Date(finishRow['@timestamp']).getTime() + 1000);
                this.setToDate(toDate);
                let finishRowFullMsg: string = finishRow.message;
                this.logAnalyzerQueryModel.searchAfterTrace = startRow;
                this.setRangeByGiven(startRow['@timestamp'], finishRow['@timestamp']);
                // Load Logs
                this.logAnalyzer.selectedRow = undefined;

                this.monitoringService.searchLogAnalyzerQuery(this.logAnalyzerQueryModel).subscribe(
                  (data: any) => {
                    let logs: any[] = data;

                    let finishObj: any = logs.find((x: any) => x.message === finishRowFullMsg);
                    if (finishObj) {
                      let finishIndex: number = logs.indexOf(finishObj);
                      logs.splice(finishIndex);
                    }

                    this.loadLogByGivenData(logs);
                  },
                  (error: Error) => {
                    this.startBehaviourOnNoLogs();
                  },
                );
              } else {
                this.startBehaviourOnNoLogs();
              }
            },
            (error: Error) => {
              this.startBehaviourOnNoLogs();
            },
          );
        } else {
          this.startBehaviourOnNoLogs();
        }
      },
      (error: Error) => {
        this.startBehaviourOnNoLogs();
      },
    );
    // TODO search start MSG with  to make searchafter...
  }

  startBehaviourOnNoLogs(): void {
    this.loadLogByGivenData([]);
  }

  searchTraceByGivenMsg(
    msg: string,
    from: string | Date = this.getFromDate(),
    to: string | Date = this.getToDate(),
  ): Observable<any> {
    this.prepareLoadLog();

    return this.logAnalyzerService.searchTraceByGivenMsg(
      msg,
      this.logAnalyzer.selectedIndices,
      from,
      to,
      this.logAnalyzer.maxResults,
    );
  }

  loadComponentStreams(): void {
    let query: MonitoringQueryModel = new MonitoringQueryModel();
    query.indices = this.logAnalyzer.selectedIndices;
    query.selectedTerms.push('component', 'stream');

    this.monitoringService.searchLogsTree(query).subscribe((componentsStreams: any[]) => {
      let components: any[] = componentsStreams;
      if (this.isEmbed && this.exTJob !== undefined && this.exTJobExec !== undefined) {
        components = componentsStreams.filter((component) => {
          return component.name !== 'test';
        });
      }
      this.logAnalyzer.setComponentsStreams(components);
      this.componentsTree.treeModel.update();
    });
  }

  loadLevels(): void {
    let query: MonitoringQueryModel = new MonitoringQueryModel();
    query.indices = this.logAnalyzer.selectedIndices;
    query.selectedTerms.push('level');

    this.monitoringService.searchLogsLevelsTree(query).subscribe((levels: any[]) => {
      this.logAnalyzer.setLevels(levels);
      this.levelsTree.treeModel.update();
    });
  }
}
