import {Component, Inject, ViewChild} from '@angular/core';
import {MAT_DIALOG_DATA, MatButton, MatDialogRef} from '@angular/material';
import {FileSystemEntry} from '../../common/rest/file-system-data';
import {RestService} from '../../common/rest/rest.service';
import {BehaviorSubject} from 'rxjs';
import {FileSystemParams} from '../../common/base/file-system-params';

export class DeleteConfirmationDialogData {
  constructor(
    public readonly parent: FileSystemParams,
    public readonly entry: FileSystemEntry
  ) {
  }
}

@Component({
  selector: 'app-delete-confirmation-dialog',
  templateUrl: './delete-confirmation-dialog.component.html',
  styleUrls: ['./delete-confirmation-dialog.component.css']
})
export class DeleteConfirmationDialogComponent {

  @ViewChild('deleteButton', {static: false}) deleteButton: MatButton;

  private errorSubject = new BehaviorSubject<string>(null);
  $error = this.errorSubject.asObservable();

  constructor(
    private restService: RestService,
    public dialogRef: MatDialogRef<DeleteConfirmationDialogComponent>,
    @Inject(MAT_DIALOG_DATA) private readonly data: DeleteConfirmationDialogData
  ) {
  }

  getEntryName(): string {
    return FileSystemEntry.getFullName(this.data.entry);
  }

  getEntryType(): string {
    if (this.data.entry.isFolder) {
      return 'folder';
    }
    return 'file';
  }

  onCancelClick() {
    this.dialogRef.close();
  }

  onDeleteClick() {
    this.clearError();
    this.deleteButton.disabled = true;
    const entryName = this.getEntryName();
    const params = this.data.parent.withAppendedPath(entryName);
    this.restService.deleteFile(params)
      .subscribe({
        next: value => {
          if (value.success) {
            this.clearError();
            this.dialogRef.close(entryName);
          } else {
            this.reportError(value.errorMessage);
          }
        },
        error: err => {
          this.reportError(err.message);
        }
      });
  }

  private clearError() {
    this.errorSubject.next(null);
  }

  private reportError(msg: string) {
    this.errorSubject.next(msg);
    this.deleteButton.disabled = false;
  }
}
