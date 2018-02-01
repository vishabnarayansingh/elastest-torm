import { Component, OnInit, Input, ViewChild, ElementRef } from '@angular/core';
import { TestLinkService } from '../../testlink.service';
import { TestCaseExecutionModel } from '../../models/test-case-execution-model';
import { BuildModel } from '../../models/build-model';
import { TestCaseModel } from '../../models/test-case-model';
import { AfterViewChecked, OnChanges, SimpleChanges } from '@angular/core/src/metadata/lifecycle_hooks';
import { Observable } from 'rxjs/Observable';
import { Subject } from 'rxjs/Subject';
import { IExternalExecution } from '../../../elastest-etm/external/models/external-execution-interface';
ElementRef;
@Component({
  selector: 'testlink-execution-form',
  templateUrl: './execution-form.component.html',
  styleUrls: ['./execution-form.component.scss'],
})
export class ExecutionFormComponent implements OnInit, OnChanges, AfterViewChecked, IExternalExecution {
  @Input() data: any;

  @ViewChild('notes') notes: ElementRef;
  alreadyFocused: boolean = false;

  // TestCaseSteps Data
  testCaseStepsColumns: any[] = [
    { name: 'id', label: 'Id' },
    // { name: 'testCaseVersionId', label: 'Version Id' },
    // { name: 'number', label: 'Number' },
    { name: 'actions', label: 'Actions' },
    { name: 'expectedResults', label: 'Expected Results' },
    // { name: 'active', label: 'Active' },
    { name: 'executionType', label: 'Exec Type' },
  ];

  testCase: TestCaseModel;
  build: BuildModel;

  tcExec: TestCaseExecutionModel;

  constructor(private testLinkService: TestLinkService) {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes.data) {
      this.ngOnInit();
      this.alreadyFocused = false;
    }
  }

  ngOnInit() {
    this.tcExec = new TestCaseExecutionModel();
    this.testCase = this.data.testCase;
    this.build = this.data.build;

    this.tcExec.testCaseVersionId = this.testCase.versionId;
    this.tcExec.testCaseVersionNumber = this.testCase.version;
    this.tcExec.executionType = this.testCase.executionType;
    this.tcExec.testPlanId = this.build.testPlanId;
    this.tcExec.buildId = this.build.id;
  }

  ngAfterViewChecked() {
    if (!this.alreadyFocused) {
      this.notes.nativeElement.focus();
      this.alreadyFocused = true;
    }
  }

  saveExecution(): Observable<boolean> {
    let _obs: Subject<boolean> = new Subject<boolean>();
    let obs: Observable<boolean> = _obs.asObservable();
    if (this.data.additionalNotes) {
      this.tcExec.notes = this.tcExec.notes ? this.tcExec.notes + this.data.additionalNotes : this.data.additionalNotes;
    }
    this.testLinkService.saveExecution(this.tcExec, this.testCase.id).subscribe(
      (data) => {
        _obs.next(true);
      },
      (error) => _obs.error(error),
    );
    return obs;
  }
}
