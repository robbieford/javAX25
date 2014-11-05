fontSize = 25;

%y = value...?
fle = wavread('.\..\..\nogit\01 Track 1.wav');
y = fle(593001:593500,1);
clear fle

f = figure('Position',[0,0,1280,1024]);
set(gcf,'color','w');
plot(y);
filename = 'Example Signal Segment from the Los Angeles Recording';
title(filename);
xlabel('Sample Number');
ylabel('Magnitude');
minY = min(y);
maxY = max(y);
center = (minY+maxY)/ 2;
rangeY = maxY - minY;
adjustedRange = 0.55*rangeY;
minY = (center - adjustedRange);
maxY = (center + adjustedRange);
ylim([minY maxY]);
set(gca,'FontSize',fontSize,'fontWeight','bold');
set(findall(gcf,'type','text'),'FontSize',fontSize,'fontWeight','bold');
yL = get(gca,'YLim');
saveas(f, strcat('.\..\..\..\rrxthesis\images\',regexprep(filename,'[^\w'']',''),'.png'));
pause();
close(f);
clear all;