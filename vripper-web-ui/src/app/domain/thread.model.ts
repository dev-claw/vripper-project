export class Thread {
  constructor(
    public link: string,
    public title: string,
    public threadId: number,
    public total: number,
    public forum: string = '',
    public starter: string = '',
    public lastPost: string = '',
    public lastPoster: string = '',
    public replyCount: number = 0,
    public views: number = 0,
    public newestPostId: number = 0,
    public sticky: boolean = false,
    public open: boolean = true
  ) {}
}
