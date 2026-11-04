// 좁은 화면에서 메뉴가 옆으로 넘칠 때, 지금 페이지 메뉴가 보이도록 메뉴 줄만 가로로 옮긴다(페이지는 움직이지 않는다).
const nav = document.querySelector<HTMLElement>(".site-header .nav");
const current = nav?.querySelector<HTMLElement>('[aria-current="page"]');
if (nav && current && nav.scrollWidth > nav.clientWidth) {
  nav.scrollLeft = current.offsetLeft - (nav.clientWidth - current.offsetWidth) / 2;
}
