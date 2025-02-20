import { TitlesService } from '../../../shared/services/titles.service';
import { RunTJobModalComponent } from '../run-tjob-modal/run-tjob-modal.component';
import { SutModel } from '../../sut/sut-model';
import { TJobExecModel } from '../../tjob-exec/tjobExec-model';
import { TJobExecService } from '../../tjob-exec/tjobExec.service';
import { TJobModel } from '../tjob-model';
import { TJobService } from '../tjob.service';

import { Component, OnInit, ViewContainerRef, ViewChild } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import {
  IConfirmConfig,
  TdDialogService,
  ITdDataTableSelectEvent,
  ITdDataTableSelectAllEvent,
  ITdDataTableColumn,
  TdDataTableService,
  TdPagingBarComponent,
  IPageChangeEvent,
} from '@covalent/core';
import { MatDialog, MatDialogRef } from '@angular/material';
import { Observable, Subject } from 'rxjs';

@Component({
  selector: 'etm-tjob-manager',
  templateUrl: './tjob-manager.component.html',
  styleUrls: ['./tjob-manager.component.scss'],
})
export class TjobManagerComponent implements OnInit {
  @ViewChild(TdPagingBarComponent) execsPaging: TdPagingBarComponent;

  tJob: TJobModel;
  editMode: boolean = false;

  sutEmpty: SutModel = new SutModel();
  deletingInProgress: boolean = false;

  // tJobExecData: TJobExecModel[] = [];
  tJobExecDataByPage: TJobExecModel[][] = [];

  showSpinner: boolean = true;

  selectedExecsIds: number[] = [];
  execIdsWithErrorOnDelete: number[] = [];

  // TJob Exec Data
  tJobExecColumns: ITdDataTableColumn[] = [
    { name: 'id', label: 'Id', width: 80 },
    { name: 'result', label: 'Result' },
    { name: 'executionDate', label: 'Execution Date', width: 125 },
    { name: 'startDate', label: 'Start Date' },
    { name: 'endDate', label: 'End Date' },
    { name: 'duration', label: 'Duration(sec)', width: 112 },
    { name: 'sutExecution', label: 'Sut Execution', width: 120 },
    { name: 'monitoringStorageType', label: 'Mon. Storage', width: 123 },
    { name: 'options', label: 'Options', sortable: false },
  ];

  // Pagination
  fromExecsRow: number = 1;
  currentExecsPage: number = 1;
  execsPageSize: number = 10;
  // Aux Page size
  currentExecsPageSize: number = this.execsPageSize;
  execsFilteredTotal: number;
  execsFilteredData: TJobExecModel[] = [];

  constructor(
    private titlesService: TitlesService,
    private tJobService: TJobService,
    private tJobExecService: TJobExecService,
    private route: ActivatedRoute,
    private router: Router,
    private _dialogService: TdDialogService,
    private _viewContainerRef: ViewContainerRef,
    public dialog: MatDialog,
    private dataTableService: TdDataTableService,
  ) {}

  ngOnInit(): void {
    this.tJob = new TJobModel();
    this.reloadTJob();
  }

  reloadTJob(): void {
    if (this.route.params !== null || this.route.params !== undefined) {
      this.route.params
        .switchMap((params: Params) => this.tJobService.getTJob(params['tJobId']))
        .subscribe((tJob: TJobModel) => {
          this.tJob = tJob;
          this.titlesService.setHeadTitle('TJob ' + this.tJob.name);
          this.titlesService.setPathName(this.router.routerState.snapshot.url);
          if (this.tJob.sut.id === 0) {
            this.tJob.sut = this.sutEmpty;
          }
          this.tJobExecService.getLastNTJobExecutions(tJob.id, this.execsPageSize, true).subscribe(
            (tJobExecs: TJobExecModel[]) => {
              this.tJobExecDataByPage = [];
              this.fromExecsRow = 1;
              this.currentExecsPage = 1;

              this.tJobExecDataByPage[this.currentExecsPage - 1] = tJobExecs;
              this.showSpinner = false;
              this.filterExecs();
            },
            (error: Error) => console.log(error),
          );
        });
    }
  }

  deleteTJobExec(tJobExec: TJobExecModel): void {
    let iConfirmConfig: IConfirmConfig = {
      message: 'TJob Execution ' + tJobExec.id + ' will be deleted, do you want to continue?',
      disableClose: false,
      viewContainerRef: this._viewContainerRef,
      title: 'Confirm',
      cancelButton: 'Cancel',
      acceptButton: 'Yes, delete',
    };
    this._dialogService
      .openConfirm(iConfirmConfig)
      .afterClosed()
      .subscribe((accept: boolean) => {
        if (accept) {
          this.deletingInProgress = true;
          this.tJobExecService.deleteTJobExecution(this.tJob, tJobExec).subscribe(
            (exec) => {
              this.deletingInProgress = false;
              this.tJobExecService.popupService.openSnackBar(
                'TJob Execution Nº' + tJobExec.id + ' has been removed successfully!',
              );
              this.reloadTJob();
            },
            (error: Error) => {
              this.deletingInProgress = false;
              this.tJobExecService.popupService.openSnackBar('TJob Execution could not be deleted');
            },
          );
        }
      });
  }

  viewTJobExec(tJobExec: TJobExecModel): void {
    this.router.navigate(['/projects', this.tJob.project.id, 'tjob', this.tJob.id, 'tjob-exec', tJobExec.id]);
  }

  runTJob(): void {
    if (this.tJob.hasParameters()) {
      let dialogRef: MatDialogRef<RunTJobModalComponent> = this.dialog.open(RunTJobModalComponent, {
        data: this.tJob.cloneTJob(),
        height: '85%',
        width: '65%',
      });
    } else {
      this.tJobExecService.runTJob(this.tJob.id, undefined, undefined, this.tJob.multiConfigurations).subscribe(
        (tjobExecution: TJobExecModel) => {
          this.router.navigate(['/projects', this.tJob.project.id, 'tjob', this.tJob.id, 'tjob-exec', tjobExecution.id]);
        },
        (error: Error) => console.error('Error:' + error),
      );
    }
  }

  editSut(): void {
    this.router.navigate(['/projects', this.tJob.project.id, 'sut', 'edit', this.tJob.sut.id]);
  }

  editTJob(): void {
    if (this.tJob.external && this.tJob.getExternalEditPage()) {
      window.open(this.tJob.getExternalEditPage());
    } else {
      this.router.navigate(['/projects', this.tJob.project.id, 'tjob', 'edit', this.tJob.id]);
    }
  }

  deleteTJob(): void {
    let iConfirmConfig: IConfirmConfig = {
      message: 'TJob ' + this.tJob.id + ' will be deleted with all TJob Executions, do you want to continue?',
      disableClose: false,
      viewContainerRef: this._viewContainerRef,
      title: 'Confirm',
      cancelButton: 'Cancel',
      acceptButton: 'Yes, delete',
    };
    this._dialogService
      .openConfirm(iConfirmConfig)
      .afterClosed()
      .subscribe((accept: boolean) => {
        if (accept) {
          this.deletingInProgress = true;
          this.tJobService.deleteTJob(this.tJob).subscribe(
            (tJob) => {
              this.deletingInProgress = true;
              this.router.navigate(['/projects']);
            },
            (error: Error) => {
              this.deletingInProgress = true;
              console.log(error);
            },
          );
        }
      });
  }

  viewInLogAnalyzer(tJobExec: TJobExecModel): void {
    this.router.navigate(['/loganalyzer'], { queryParams: { tjob: this.tJob.id, exec: tJobExec.id } });
  }

  switchExecSelectionByData(tJobExec: TJobExecModel, selected: boolean): void {
    if (selected) {
      this.selectedExecsIds.push(tJobExec.id);
    } else {
      const index: number = this.selectedExecsIds.indexOf(tJobExec.id, 0);
      if (index > -1) {
        this.selectedExecsIds.splice(index, 1);
      }
    }
  }

  switchExecSelection(event: ITdDataTableSelectEvent): void {
    if (event && event.row) {
      let tJobExec: TJobExecModel = event.row;
      this.switchExecSelectionByData(tJobExec, event.selected);
    }
  }

  switchAllExecsSelection(event: ITdDataTableSelectAllEvent): void {
    if (event && event.rows) {
      for (let tJobExec of event.rows) {
        this.switchExecSelectionByData(tJobExec, event.selected);
      }
    }
  }

  compareExecutions(): void {
    this.router.navigate(['/projects', this.tJob.project.id, 'tjob', this.tJob.id, 'comparator'], {
      queryParams: { execs: this.selectedExecsIds.join(',') },
    });
  }

  removeSelectedExecs(): void {
    if (this.selectedExecsIds.length > 0) {
      let iConfirmConfig: IConfirmConfig = {
        message: 'Selected Executions will be deleted, do you want to continue?',
        disableClose: false, // defaults to false
        viewContainerRef: this._viewContainerRef,
        title: 'Confirm',
        cancelButton: 'Cancel',
        acceptButton: 'Yes, delete',
      };
      this._dialogService
        .openConfirm(iConfirmConfig)
        .afterClosed()
        .subscribe((accept: boolean) => {
          if (accept) {
            this.deletingInProgress = true;

            this.execIdsWithErrorOnDelete = [];
            this.removeMultipleExecutionsRecursively([...this.selectedExecsIds]).subscribe(
              (end: boolean) => {
                if (this.execIdsWithErrorOnDelete.length > 0) {
                  let errorMsg: string = 'Error on delete execs with ids: ' + this.execIdsWithErrorOnDelete;
                  this.tJobExecService.popupService.openSnackBar(errorMsg);
                } else {
                  this.tJobExecService.popupService.openSnackBar('Executions ' + this.selectedExecsIds + ' has been removed!');
                }

                this.deletingInProgress = false;
                this.reloadTJob();
                this.execIdsWithErrorOnDelete = [];
                this.selectedExecsIds = [];
              },
              (error: Error) => {
                console.log(error);
                this.deletingInProgress = false;
                this.reloadTJob();
                this.execIdsWithErrorOnDelete = [];
                this.selectedExecsIds = [];
              },
            );
          }
        });
    }
  }

  removeMultipleExecutionsRecursively(selectedExecsIds: number[], _obs: Subject<any> = new Subject<any>()): Observable<boolean> {
    let obs: Observable<any> = _obs.asObservable();

    if (selectedExecsIds.length > 0) {
      let execId: number = selectedExecsIds.shift();

      this.tJobExecService.deleteTJobExecutionById(this.tJob, execId).subscribe(
        (tJobExec: TJobExecModel) => {
          this.removeMultipleExecutionsRecursively(selectedExecsIds, _obs);
        },
        (error: Error) => {
          console.log(error);
          this.execIdsWithErrorOnDelete.push(execId);
          this.removeMultipleExecutionsRecursively(selectedExecsIds, _obs);
        },
      );
    } else {
      _obs.next(true);
    }

    return obs;
  }

  execsPage(pagingEvent: IPageChangeEvent): void {
    // On change page size, reload and start from row 1
    if (this.currentExecsPageSize !== pagingEvent.pageSize) {
      this.currentExecsPageSize = this.execsPageSize;
      this.reloadTJob();
      return;
    }
    this.execsPageSize = pagingEvent.pageSize;
    this.currentExecsPageSize = this.execsPageSize;
    this.fromExecsRow = pagingEvent.fromRow;
    this.currentExecsPage = pagingEvent.page;
    this.filterExecs();
  }

  async filterExecs(): Promise<void> {
    let newData: any[] = this.tJobExecDataByPage[this.currentExecsPage - 1];
    if (!newData || newData.length === 0) {
      this.tJobExecDataByPage[this.currentExecsPage - 1] = await this.loadNextTJobExecs();
      newData = this.tJobExecDataByPage[this.currentExecsPage - 1];
    }
    this.execsFilteredTotal = this.tJob.tjobExecs.length;
    newData = await this.dataTableService.pageData(newData, 1, this.execsPageSize);
    this.execsFilteredData = newData;
  }

  async loadNextTJobExecs(): Promise<TJobExecModel[]> {
    // pages in backend starts at 0
    let page: number = this.currentExecsPage - 1;

    return this.tJobExecService.getTJobExecsPageSinceId(this.tJob.id, page, this.execsPageSize, 'desc', true).toPromise();
  }
}
