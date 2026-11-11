import { DataSource, SelectionModel } from '@angular/cdk/collections';
import {
  Overlay,
  OverlayModule,
  OverlayPositionBuilder,
} from '@angular/cdk/overlay';
import { ComponentPortal, PortalModule } from '@angular/cdk/portal';
import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ComponentRef,
  EventEmitter,
  Output,
  signal,
} from '@angular/core';
import { MatCheckboxModule } from '@angular/material/checkbox';
import {
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { BehaviorSubject, Observable, Subscription, take } from 'rxjs';
import {
  ConfirmComponent,
  ConfirmDialogData,
} from '../confirm/confirm.component';
import { ThreadRow } from '../domain/thread-row.model';
import { Thread } from '../domain/thread.model';
import { ApplicationEndpointService } from '../services/application-endpoint.service';
import { ThreadContextmenuComponent } from '../thread-contextmenu/thread-contextmenu.component';
import {
  ThreadDialogData,
  ThreadSelectionComponent,
} from '../thread-selection/thread-selection.component';
import { isDisplayed } from '../utils/utils';

@Component({
  selector: 'app-thread-table',
  imports: [
    CommonModule,
    OverlayModule,
    PortalModule,
    MatDialogModule,
    MatCheckboxModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
  ],
  templateUrl: './thread-table.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
})
export class ThreadTableComponent {
  dataSource = new ThreadDataSource(this.applicationEndpoint);
  displayedColumns: string[] = ['title', 'link', 'total', 'forum', 'starter', 'lastPost', 'lastPoster', 'replyCount', 'views'];
  selection = new SelectionModel<ThreadRow>(
    true,
    [],
    true,
    (a, b) => a.link === b.link
  );
  isDisplayed = isDisplayed;
  @Output()
  rowCountChange = new EventEmitter<number>();
  @Output()
  selectedChange = new EventEmitter<Thread[]>();
  columnsToDisplay = signal([...this.displayedColumns]);

  constructor(
    private applicationEndpoint: ApplicationEndpointService,
    private overlayPositionBuilder: OverlayPositionBuilder,
    private overlay: Overlay,
    private dialog: MatDialog
  ) {
    this.dataSource._dataStream.subscribe((v) =>
      this.rowCountChange.emit(v.length)
    );
    this.selection.changed.subscribe(() =>
      this.selectedChange.emit(this.selection.selected)
    );
  }

  onRowDoubleClicked(row: ThreadRow) {
    const data: ThreadDialogData = { threadId: row.threadId };
    this.dialog.open(ThreadSelectionComponent, {
      data,
      maxWidth: '100vw',
      maxHeight: '100vh',
      width: '80vw',
      height: '80vh',
    });
  }

  onClick(row: ThreadRow) {
    this.selection.clear();
    this.selection.select(row);
  }

  onContextMenu(mouseEvent: MouseEvent, row: ThreadRow) {
    mouseEvent.preventDefault();
    if (this.selection.selected.length > 1) {
      this.selection.select(row);
    } else {
      this.selection.clear();
      this.selection.select(row);
    }
    const positionStrategy = this.overlayPositionBuilder
      .flexibleConnectedTo({ x: mouseEvent.x, y: mouseEvent.y })
      .withPush(true)
      .withGrowAfterOpen(true)
      .withPositions([
        {
          originX: 'start',
          originY: 'bottom',
          overlayX: 'start',
          overlayY: 'top',
        },
        {
          originX: 'start',
          originY: 'top',
          overlayX: 'start',
          overlayY: 'bottom',
        },
      ]);
    const threadContextMenuOverlayRef = this.overlay.create({
      positionStrategy,
    });
    const threadContextMenuPortal = new ComponentPortal(
      ThreadContextmenuComponent
    );
    const ref: ComponentRef<ThreadContextmenuComponent> =
      threadContextMenuOverlayRef.attach(threadContextMenuPortal);
    ref.instance.thread = row as ThreadRow;

    ref.instance.close = () => {
      threadContextMenuOverlayRef?.detach();
      threadContextMenuOverlayRef?.dispose();
      ref.destroy();
    };

    ref.instance.onThreadSelection = () => {
      const data: ThreadDialogData = { threadId: row.threadId };
      this.dialog.open(ThreadSelectionComponent, {
        data,
        maxWidth: '100vw',
        maxHeight: '100vh',
        width: '80vw',
        height: '80vh',
      });
    };

    ref.instance.onThreadDelete = () => {
      const dialog: MatDialogRef<ConfirmComponent, ConfirmDialogData> =
        this.dialog.open<ConfirmComponent, ConfirmDialogData>(
          ConfirmComponent,
          {
            data: {
              message: `Confirm removal of ${
                this.selection.selected.length
              } post${this.selection.selected.length > 1 ? 's' : ''}`,
              confirmCallback: () => {
                this.applicationEndpoint
                  .deleteThreads(this.selection.selected)
                  .subscribe(() => dialog.close());
              },
            },
          }
        );
    };

    threadContextMenuOverlayRef
      .outsidePointerEvents()
      .pipe(take(1))
      .subscribe(() => {
        threadContextMenuOverlayRef?.detach();
        threadContextMenuOverlayRef?.dispose();
        ref.destroy();
      });
  }

  /** Whether the number of selected elements matches the total number of rows. */
  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource._dataStream.value.length;
    return numSelected === numRows;
  }

  /** Selects all rows if they are not all selected; otherwise clear selection. */
  toggleAllRows() {
    if (this.isAllSelected()) {
      this.selection.clear();
      return;
    }

    this.selection.select(...this.dataSource._dataStream.value);
  }

  /** The label for the checkbox on the passed row */
  checkboxLabel(row?: ThreadRow): string {
    if (!row) {
      return `${this.isAllSelected() ? 'deselect' : 'select'} all`;
    }
    return `${this.selection.isSelected(row) ? 'deselect' : 'select'} row ${
      row.link
    }`;
  }

  disableForRows(event: MouseEvent) {
    const target = event.target as HTMLElement;
    const element = document
      .getElementById('thread-grid')
      ?.getElementsByClassName('ag-center-cols-container')
      ?.item(0) as HTMLElement;
    if (element.contains(target)) {
      event.preventDefault();
    }
  }
}

class ThreadDataSource extends DataSource<Thread> {
  subscriptions: Subscription[] = [];
  _dataStream = new BehaviorSubject<Thread[]>([]);

  constructor(private applicationEndpoint: ApplicationEndpointService) {
    super();
  }

  connect(): Observable<Thread[]> {
    this.subscriptions.push(
      this.applicationEndpoint.threads$.subscribe((newThreads: Thread[]) => {
        newThreads.forEach((thread) => {
          const rowNode = this._dataStream.value.find(
            (d) => d.link === thread.link
          );
          if (rowNode == null) {
            this._dataStream.next([
              ...this._dataStream.value,
              new ThreadRow(
                thread.link,
                thread.title,
                thread.threadId,
                thread.total,
                thread.forum,
                thread.starter,
                thread.lastPost,
                thread.lastPoster,
                thread.replyCount,
                thread.views,
                thread.newestPostId,
                thread.sticky,
                thread.open
              ),
            ]);
          } else {
            Object.assign(rowNode, thread);
          }
        });
      })
    );
    this.subscriptions.push(
      this.applicationEndpoint.threadRemove$.subscribe((e: number) => {
        this._dataStream.next([
          ...this._dataStream.value.filter((v) => v.threadId !== e),
        ]);
      })
    );
    this.subscriptions.push(
      this.applicationEndpoint.threadRemoveAll$.subscribe(() => {
        this._dataStream.next([]);
      })
    );
    return this._dataStream.asObservable();
  }

  disconnect(): void {
    this.subscriptions.forEach((e) => e.unsubscribe());
  }
}
